(ns scroll.pit
  (:require [org.httpkit.client :as http]
            [jsonista.core :as json]))

(def mapper (json/object-mapper {:decode-key-fn true}))

(defn init
  ([es-host index-name] (init es-host index-name {}))
  ([es-host index-name opts]
   @(http/request
      {:method :post
       :url    (format "%s/%s/_pit?keep_alive=%s"
                       es-host index-name (or (:keep-alive opts) "1m"))}
      (fn [resp] (json/read-value (:body resp) mapper)))))

(defn terminate [es-host pit]
  @(http/request
     {:method :delete
      :headers {"Content-Type" "application/json"}
      :url    (format "%s/_pit" es-host)
      :body (json/write-value-as-string pit)}
     (fn [resp]
       (json/read-value (:body resp) mapper))))

(comment
  (scroll.pit/init "http://localhost:9200" ".kibana")

  (scroll.pit/terminate "http://localhost:9200" (scroll.pit/init "http://localhost:9200" ".kibana")))
