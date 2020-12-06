(ns scroll.pit
  (:require [clojure.tools.logging :as log]
            [scroll.request :as request]))

(defn init
  ([es-host index-name] (init es-host index-name {}))
  ([es-host index-name opts]
   (request/execute-request
     {:method :post
      :url    (format "%s/%s/_pit?keep_alive=%s"
                      es-host index-name (or (:keep-alive opts) "1m"))
      :opts   (merge request/default-exponential-backoff-params
                     (assoc opts :keywordize? true))})))

(defn terminate
  ([es-host pit] (terminate es-host pit {}))
  ([es-host pit opts]
   (log/debugf "Terminating PIT: %s" pit)
   (try
     (request/execute-request
       {:method :delete
        :url    (format "%s/_pit" es-host)
        :body   pit
        :opts   (merge request/default-exponential-backoff-params
                       (assoc opts :keywordize? true
                                   :max 1000))})
     (catch Exception _
       {:succeeded false}))))

(comment
  (scroll.pit/init "http://localhost:9200" ".kibana")

  (scroll.pit/terminate "http://localhost:9200" (scroll.pit/init "http://localhost:9200" ".kibana")))
