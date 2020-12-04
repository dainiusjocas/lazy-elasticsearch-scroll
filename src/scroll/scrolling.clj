(ns scroll.scrolling
  (:require [clojure.tools.logging :as log]
            [scroll.batch :as batch]
            [scroll.hits :as hits]
            [scroll.request :as request]))

(def default-query {:sort ["_doc"]})

(defn start [es-host index-name query opts]
  (request/execute-request
    {:url  (format "%s/%s/_search?scroll=%s"
                   es-host (or index-name "*") (get opts :keep-context "30s"))
     :body (batch/set-batch-size (or query default-query) opts)
     :opts opts}))

(defn continue [es-host scroll-id opts]
  (request/execute-request
    {:url  (format "%s/_search/scroll" es-host)
     :body {:scroll_id scroll-id
            :scroll    (get opts :keep-context "30s")}
     :opts opts}))

(defn extract-scroll-id [batch keywordize?]
  (get batch (if keywordize? :_scroll_id "_scroll_id")))

(defn delete-scroll-context
  ([es-host] (delete-scroll-context es-host "_all"))
  ([es-host scroll-id]
   (request/execute-request
     {:method  :delete
      :url     (format "%s/_search/scroll/%s" es-host scroll-id)
      :headers {"Content-Type" "application/json"}})))

(defn fetch [{:keys [es-host index-name query scroll-id opts] :as req}]
  (try
    (let [batch (if scroll-id
                  (continue es-host scroll-id opts)
                  (start es-host index-name query opts))]
      (log/debugf "Fetching a batch took: %s ms" (or (get batch :took) (get batch "took")))
      (if-let [current-hits (seq (hits/extract-hits batch (get opts :keywordize?)))]
        (lazy-cat current-hits
                  (fetch (assoc req :scroll-id (extract-scroll-id batch (get opts :keywordize?)))))
        (when (get opts :cleanup?)
          (log/infof "No more hits cleaning up the scroll: '%s'" scroll-id)
          (delete-scroll-context es-host scroll-id)
          nil)))
    (catch Exception e
      (log/errorf "Failed to scroll: %s" e)
      [])))
