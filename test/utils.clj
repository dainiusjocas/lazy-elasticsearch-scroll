(ns utils
  (:require
    [clojure.string :as string]
    [jsonista.core :as json]
    [org.httpkit.client :as http]
    [scroll :as scroll])
  (:import (java.util UUID)))

(defn index-exists? [es-host index-name]
  @(http/request
     {:method :head
      :client @scroll/client
      :url    (format "%s/%s" es-host index-name)}
     (fn [resp] (not (= 404 (:status resp))))))

(defn delete-index [es-host index-name]
  (if (index-exists? es-host index-name)
    @(http/request
       {:method  :delete
        :client  @scroll/client
        :url     (format "%s/%s" es-host index-name)
        :headers {"Content-Type" "application/json"}}
       (fn [resp]
         (json/read-value (:body resp)
                          (json/object-mapper {:decode-key-fn true}))))
    {:message "Index does not exist."}))

(defn create-index [es-host index-name]
  @(http/request
     {:method  :put
      :client  @scroll/client
      :url     (format "%s/%s" es-host index-name)
      :headers {"Content-Type" "application/json"}
      :body    (json/write-value-as-string {})}
     (fn [resp]
       (json/read-value (:body resp)
                        (json/object-mapper {:decode-key-fn true})))))

(defn refresh-index [dest-host dest-index]
  @(http/request
     {:method  :get
      :client  @scroll/client
      :url     (format "%s/%s/_refresh" dest-host dest-index)
      :headers {"Content-Type" "application/json"}}
     (fn [resp]
       (json/read-value (:body resp)
                        (json/object-mapper {:decode-key-fn true})))))

(defn es-version [es-host]
  @(org.httpkit.client/request
     {:method :get
      :url    es-host}
     #(-> (:body %)
          (json/read-value (json/object-mapper {:decode-key-fn true}))
          :version
          :number
          (first)
          (str))))

(defn update-op [es-version index-name id]
  (let [id (or id (UUID/randomUUID))]
    (case es-version
      "5" {:index {"_index" index-name "_type" "doc" "_id" id}}
      "6" {:index {"_index" index-name "_type" "_doc" "_id" id}}
      "7" {:index {"_index" index-name "_id" id}}
      {:index {"_index" index-name "_id" id}})))

(defn bulk-item-update [version record index-name]
  (str
    (json/write-value-as-string
      (update-op version index-name (get record :_id)))
    "\n"
    (json/write-value-as-string (get record :_source))))

(defn updates->body [version items index-name]
  (str
    (string/join
      "\n"
      (map #(bulk-item-update version % index-name) items))
    "\n"))

(defn bulk-store! [es-host body]
  @(http/request
     {:url       (format "%s/_bulk" es-host)
      :method    :post
      :headers   {"Content-Type" "application/x-ndjson"}
      :body      body
      :keepalive 30000}
     (fn [response]
       (update response :body #(json/read-value % (json/object-mapper
                                                    {:decode-key-fn true}))))))

(defn fill-index [es-host index-name records]
  (let [version (es-version es-host)]
    (bulk-store! es-host (updates->body version records index-name)))
  (refresh-index es-host index-name))
