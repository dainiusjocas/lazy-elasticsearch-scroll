(ns scroll.search-after
  (:require [clojure.tools.logging :as log]
            [scroll.batch :as batch]
            [scroll.hits :as hits]
            [scroll.request :as request]))

(def default-query {:sort ["_doc"]})

(defn remove-from-param [query]
  (dissoc query :from))

(defn prepare-url [es-host index-name query]
  (if (:pit query)
    (format "%s/_search" es-host)
    (format "%s/%s/_search" es-host (or index-name "*"))))

(defn prepare-query-body [query opts]
  (cond-> (or query default-query)
          (nil? (:sort query)) (merge default-query)
          true (batch/set-batch-size opts)
          true (remove-from-param)
          true (assoc :track_total_hits true)))

(defn start [es-host index-name query opts]
  (request/execute-request
    {:url  (prepare-url es-host index-name query)
     :body (prepare-query-body query opts)
     :opts opts}))

(defn continue [es-host index-name query search-after-clause opts]
  (request/execute-request
    {:url  (prepare-url es-host index-name query)
     :body (assoc (prepare-query-body query opts) :search_after search-after-clause)
     :opts opts}))

(defn extract-search-after [batch keywordize?]
  (if keywordize?
    (-> (get-in batch [:hits :hits])
        last
        (get :sort))
    (-> (get-in batch ["hits" "hits"])
        last
        (get "sort"))))

(defn extract-pit-id [batch keywordize?]
  (get batch (if keywordize? :pit_id "pit_id")))

(defn fetch [{:keys [es-host index-name query search-after fetched opts] :as req}]
  (try
    (let [batch (if search-after
                  (continue es-host index-name query search-after opts)
                  (start es-host index-name query opts))
          req (if search-after
                req
                (assoc req :total-count (if (get opts :keywordize?)
                                          (get-in batch [:hits :total :value])
                                          (get-in batch ["hits" "total" "value"]))))]
      (log/debugf "Fetching a batch from '%s' out of '%s' took: %s ms"
                  (or fetched 0)
                  (get req :total-count)
                  (or (get batch :took) (get batch "took")))
      (when (:latest-pit-id opts)
        (reset! (:latest-pit-id opts) (extract-pit-id batch (get opts :keywordize?))))
      (when-let [current-hits (seq (hits/extract-hits batch (get opts :keywordize?)))]
        (lazy-cat current-hits
                  (when-let [sa (extract-search-after batch (get opts :keywordize?))]
                    (fetch (-> req
                               (assoc :search-after sa)
                               (assoc :fetched (+ (or fetched 0) (count current-hits)))))))))
    (catch Exception e
      (.printStackTrace e)
      (log/errorf "Failed to search_after: %s" e)
      [])))
