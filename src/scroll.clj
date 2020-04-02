(ns scroll
  (:require
    [clojure.tools.logging :as log]
    [org.httpkit.client :as http]
    [jsonista.core :as json])
  (:import
    (javax.net.ssl SSLParameters SSLEngine SNIHostName)
    (java.net URI)))

(def default-exponential-backoff-params
  {:time 1000
   :rate 2
   :max  60000
   :p?   identity})

(defn exponential-backoff
  ([f] (exponential-backoff f default-exponential-backoff-params))
  ([f {:keys [time rate max p?] :as opts}]
   (if (>= time max) ;; we're over budget, just call f
     (f)
     (try
       (f)
       (catch Throwable t
         (if (p? t)
           (do
             (Thread/sleep time)
             (exponential-backoff f (assoc opts :rate (* time rate))))
           (throw t)))))))

(defn sni-configure
  [^SSLEngine ssl-engine ^URI uri]
  (let [^SSLParameters ssl-params (.getSSLParameters ssl-engine)]
    (.setServerNames ssl-params [(SNIHostName. (.getHost uri))])
    (.setSSLParameters ssl-engine ssl-params)))

(def client (delay (http/make-client {:ssl-configurer sni-configure})))

(defn execute-request [{:keys [url body opts]}]
  (exponential-backoff
    (fn []
      @(http/request
         {:method  :get
          :client  @client
          :url     url
          :headers {"Content-Type" "application/json"}
          :body    (json/write-value-as-string body)}
         (fn [{:keys [status body error]}]
           (when error (throw error))
           (let [{:keys [error] :as decoded-body}
                 (json/read-value body (json/object-mapper
                                         {:decode-key-fn (get opts :keywordize?)}))]
             (when error (throw (Exception. ^String (:reason error))))
             (if (<= 200 status 299)
               decoded-body
               (throw (Exception. "Response exception")))))))))

(def default-size 1000)
(def default-query {:sort ["_doc"]})

(defn set-batch-size [query opts]
  (assoc query :size (or (:size opts) (:size query) default-size)))

(defn start [es-host index-name query opts]
  (execute-request
    {:url  (format "%s/%s/_search?scroll=%s"
                   es-host (or index-name "*") (get opts :keep-context "30s"))
     :body (set-batch-size (or query default-query) opts)
     :opts opts}))

(defn continue [es-host scroll-id opts]
  (execute-request
    {:url  (format "%s/_search/scroll" es-host)
     :body {:scroll_id scroll-id
            :scroll    (get opts :keep-context "30s")}
     :opts opts}))

(defn extract-hits [batch keywordize?]
  (get-in batch (if keywordize? [:hits :hits] ["hits" "hits"])))

(defn extract-scroll-id [batch keywordize?]
  (get batch (if keywordize? :_scroll_id "_scroll_id")))

(defn fetch [{:keys [es-host index-name query scroll-id opts] :as req}]
  (let [batch (if scroll-id
                (continue es-host scroll-id opts)
                (start es-host index-name query opts))]
    (log/debugf "Fetching a batch took: %s ms" (or (get batch :took) (get batch "took")))
    (when-let [current-hits (seq (extract-hits batch (get opts :keywordize?)))]
      (lazy-cat current-hits
                (fetch (assoc req :scroll-id (extract-scroll-id batch (get opts :keywordize?))))))))

(defn dissoc-aggs [scroll-request]
  (-> scroll-request
      (update :query dissoc :aggs)
      (update :query dissoc "aggs")))

(defn hits
  "Returns a lazy sequence of hits from Elasticsearch using Scroll API.
  See: https://www.elastic.co/guide/en/elasticsearch/reference/7.5/search-request-body.html#request-body-search-scroll

  Params:
  - es-host: Elasticsearch host, e.g. `http://localhost:9200`
  - index-name: indices through which to scroll, default `*`, i.e. all indices
  - query: Elasticsearch query to filter the documents, default: `{:sort [\"_doc\"]}`
  - opts: supported options:
  - - keep-context: specifies how log to maintain scroll state, default `30s`
  - - keywordize?: should the JSON keys be converted to Clojure keys, default true
  - - size: how many records should be fetched from Elasticsearch in one network trip, default 1000"
  [{:keys [es-host] :as scroll-request}]
  (assert (string? es-host) (format "Invalid Elasticsearch host `%s`" es-host))
  (log/infof "Started scrolling with: '%s'" scroll-request)
  (fetch (cond-> scroll-request
                 true (update-in [:opts :keywordize?] #(not (false? %)))
                 (not (true? (get-in scroll-request [:opts :preserve-aggs?]))) (dissoc-aggs))))

(comment
  (hits
    {:es-host    "http://localhost:9200"
     :index-name ".kibana"
     :query      {:query {:match_all {}}}
     :opts       {:keep-context "30s"
                  :keywordize?  true
                  :size         1000}})
  (hits
    {:es-host    "http://localhost:9200"
     :index-name ".kibana"
     :query      {:query {:match_all {}}
                  :aggs {:my-aggregation
                         {:terms {:field :_id}}}}
     :opts       {:keep-context "30s"
                  :keywordize?  true
                  :size         1000
                  :preserve-aggs? true}}))
