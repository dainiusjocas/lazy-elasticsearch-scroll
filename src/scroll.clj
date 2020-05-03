(ns scroll
  (:require [clojure.tools.logging :as log]
            [scroll.request :as request]
            [scroll.search-after :as search-after]
            [scroll.scrolling :as scrolling]))

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
  (let [scroll-strategy (get-in scroll-request [:opts :strategy])
        params (cond-> scroll-request
                       true (update-in [:opts :keywordize?] #(not (false? %)))
                       true (update :opts (fn [opts] (merge request/default-exponential-backoff-params opts)))
                       (not (true? (get-in scroll-request [:opts :preserve-aggs?]))) (dissoc-aggs))]
    (case scroll-strategy
      :scroll-api (scrolling/fetch params)
      :search-after (search-after/fetch params)
      (scrolling/fetch params))))

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
