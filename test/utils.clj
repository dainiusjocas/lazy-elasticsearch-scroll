(ns utils
  (:require
    [clojure.string :as string]
    [jsonista.core :as json]
    [org.httpkit.client :as http]
    [scroll.request :as request])
  (:import (java.util UUID)))

(defn index-exists? [es-host index-name]
  (not (= 404 (:status (request/execute-request
                         {:method :head
                          :url (format "%s/%s" es-host index-name)})))))

(defn delete-index [es-host index-name]
  (if (index-exists? es-host index-name)
    @(http/request
       {:method  :delete
        :client  @request/client
        :url     (format "%s/%s" es-host index-name)
        :headers {"Content-Type" "application/json"}}
       (fn [resp]
         (json/read-value (:body resp)
                          (json/object-mapper {:decode-key-fn true}))))
    {:message "Index does not exist."}))

(defn create-index [es-host index-name]
  @(http/request
     {:method  :put
      :client  @request/client
      :url     (format "%s/%s" es-host index-name)
      :headers {"Content-Type" "application/json"}
      :body    (json/write-value-as-string {:settings
                                            {:index
                                             {:number_of_shards 1
                                              :number_of_replicas 0}}})}
     (fn [resp]
       (json/read-value (:body resp)
                        (json/object-mapper {:decode-key-fn true})))))

(defn recreate-index [es-host index-name]
  (delete-index es-host index-name)
  (create-index es-host index-name))

(defn refresh-index [dest-host dest-index]
  @(http/request
     {:method  :get
      :client  @request/client
      :url     (format "%s/%s/_refresh" dest-host dest-index)
      :headers {"Content-Type" "application/json"}}
     (fn [resp]
       (json/read-value (:body resp)
                        (json/object-mapper {:decode-key-fn true})))))

(defn es-version [es-host]
  @(http/request
     {:method :get
      :url    es-host}
     #(-> (:body %)
          (json/read-value (json/object-mapper {:decode-key-fn true}))
          :version
          :number
          (first)
          (str))))

(defn version->semantic-version [version-str]
  (zipmap [:major :minor :patch]
          (map (fn [^String n] (try (Integer/parseInt n) (catch Exception _ n)))
               (string/split version-str #"\."))))

(defn semantic-es-version [es-host]
  @(http/request
     {:method :get
      :url    es-host}
     (fn [resp]
       (-> (:body resp)
           (json/read-value (json/object-mapper {:decode-key-fn true}))
           :version
           :number
           version->semantic-version))))

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
      :client    @request/client
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
