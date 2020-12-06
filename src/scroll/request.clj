(ns scroll.request
  (:require [clojure.tools.logging :as log]
            [clojure.string :as str]
            [org.httpkit.client :as http]
            [jsonista.core :as json])
  (:import (javax.net.ssl SSLEngine SSLParameters SNIHostName)
           (java.net URI)
           (java.util Base64)))

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
         (log/errorf "Exponential backoff failed with '%s'" t)
         (if (p? t)
           (do
             (Thread/sleep time)
             (exponential-backoff f (assoc opts :time (* time rate))))
           (throw t)))))))

(defn sni-configure
  [^SSLEngine ssl-engine ^URI uri]
  (let [^SSLParameters ssl-params (.getSSLParameters ssl-engine)]
    (.setServerNames ssl-params [(SNIHostName. (.getHost uri))])
    (.setSSLParameters ssl-engine ssl-params)))

(def client (delay (http/make-client {:ssl-configurer sni-configure})))

(def default-headers {"Content-Type"  "application/json"})

(defn authorization-token []
  (let [username (System/getenv "ELASTIC_USERNAME")
        password (System/getenv "ELASTIC_PASSWORD")]
    (when (and username password)
      (.encodeToString (Base64/getEncoder) (.getBytes (str username ":" password))))))

(defn prepare-headers [headers _]
  ; basic authorization
  ; https://www.elastic.co/guide/en/elasticsearch/reference/current/http-clients.html
  (if-let [auth-token (authorization-token)]
    (assoc headers "Authorization" (str "Basic " auth-token))
    headers))

(defn execute-request [{:keys [url body opts method headers]}]
  (exponential-backoff
    (fn []
      (let [{:keys [status body error]}
            @(http/request
               (cond-> {:method  (or method :get)
                        :client  @client
                        :url     url
                        :headers (prepare-headers (or headers default-headers) opts)}
                       (not (nil? body)) (assoc :body (json/write-value-as-string body))))]
        (when error (throw (Exception. (str error))))
        (when-not (str/blank? body)
          (let [{:keys [error] :as decoded-body}
                (json/read-value body (json/object-mapper
                                        {:decode-key-fn (get opts :keywordize?)}))]

            (when error (throw (Exception. (str error))))
            (if (<= 200 status 299)
              decoded-body
              (throw (Exception. (format "Response exception: %s" (str decoded-body)))))))))
    (or opts default-exponential-backoff-params)))
