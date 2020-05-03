(ns scroll.search-after
  (:require [clojure.tools.logging :as log]
            [scroll.batch :as batch]
            [scroll.hits :as hits]
            [scroll.request :as request]))

(def default-query {:sort ["_doc"]})

(defn remove-from-param [query]
  (dissoc query :from))

(defn start [es-host index-name query opts]
  (request/execute-request
    {:url  (format "%s/%s/_search" es-host (or index-name "*"))
     :body (remove-from-param
             (batch/set-batch-size
               (if (:sort query)
                 (or query default-query)
                 (assoc query :sort [:_doc]))
               opts))
     :opts opts}))

(defn continue [es-host index-name query search-after opts]
  (request/execute-request
    {:url  (format "%s/%s/_search" es-host (or index-name "*"))
     :body (remove-from-param
             (batch/set-batch-size
               (if (:sort query)
                 (assoc (or query default-query) :search_after search-after)
                 (assoc query :sort [:_doc]
                              :search_after search-after)) opts))
     :opts opts}))

(defn extract-search-after [batch keywordize?]
  (if keywordize?
    (-> (get-in batch [:hits :hits])
        last
        (get :sort))
    (-> (get-in batch ["hits" "hits"])
         last
         (get "sort"))))

(defn fetch [{:keys [es-host index-name query search-after opts] :as req}]
  (try
    (let [batch (if search-after
                  (continue es-host index-name query search-after opts)
                  (start es-host index-name query opts))]
      (log/debugf "Fetching a batch took: %s ms" (or (get batch :took) (get batch "took")))
      (when-let [current-hits (seq (hits/extract-hits batch (get opts :keywordize?)))]
        (lazy-cat current-hits
                  (when-let [sa (extract-search-after batch (get opts :keywordize?))]
                    (fetch (assoc req :search-after sa))))))
    (catch Exception e
      (.printStackTrace e)
      (log/errorf "Failed to search_after: %s" e)
      [])))
