(ns exponential-backoff-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.tools.logging :as log]
            [utils :as utils]))

(deftest ^:integration exponential-backoff-fail
  (testing "if batch size is equal 0 then empty list of records should be returned with an error in the log"
    (let [es-host (or (System/getenv "ES_HOST") "http://localhost:9200")
          index-name "non-existing-index"
          start (System/currentTimeMillis)]
      (log/infof "Deleted index='%s' at '%s': %s"
                 index-name es-host (utils/delete-index es-host index-name))
      (try
        (scroll/hits
             {:es-host    es-host
              :index-name index-name
              :opts       {:size 1
                           :time 1000
                           :rate 2
                           :max 1500}})
        (catch Exception _
          (is (< (- (System/currentTimeMillis) start) 2000)))))))
