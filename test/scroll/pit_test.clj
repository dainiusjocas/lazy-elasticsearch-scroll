(ns scroll.pit-test
  (:require [clojure.test :refer [deftest is]]
            [clojure.tools.logging :as log]
            [scroll.pit :as pit]))

(deftest ^:integration pit-client
  (let [es-host (or (System/getenv "ES_HOST") "http://localhost:9200")
        index-name "scroll-test-index"]
    (log/infof "Index recreated %s" (utils/recreate-index es-host index-name))
    (let [pit-resp (pit/init es-host index-name)]
      (is (string? (:id pit-resp)))
      (is (true? (:succeeded (pit/terminate es-host pit-resp)))))))
