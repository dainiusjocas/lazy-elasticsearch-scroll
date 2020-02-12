(ns scroll-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [clojure.tools.logging :as log]
    [scroll :as scroll]
    [utils :as utils]))

(deftest ^:integration basic-scroll
  (let [es-host (or (System/getenv "ES_HOST") "http://localhost:9200")
        index-name "scroll-test-index"
        number-of-docs 50
        records (map (fn [x] {:_id x
                              :_source {:value x}}) (range number-of-docs))]
    (log/infof "Deleted index='%s' at '%s': %s"
               index-name es-host (utils/delete-index es-host index-name))
    (log/infof "Created index='%s' at '%s': %s" index-name es-host (utils/create-index es-host index-name))
    (utils/fill-index es-host index-name records)

    (testing "if all scroll results are fetched"
      (let [query {:query {:match_all {}}}]
        (is (= number-of-docs
               (count
                 (scroll/hits
                   {:es-host    es-host
                    :index-name index-name
                    :query      query
                    :opts       {:keywordize? true}}))))
        (is (= number-of-docs
               (count
                 (scroll/hits
                   {:es-host    es-host
                    :index-name index-name
                    :query      query
                    :opts       {:keywordize? false}}))))
        (is (= number-of-docs
               (count
                 (scroll/hits
                   {:es-host    es-host
                    :index-name index-name
                    :query      query
                    :opts       {}}))))
        (is (= number-of-docs
               (count
                 (scroll/hits
                   {:es-host    es-host
                    :index-name index-name
                    :query      query}))))
        (is (= number-of-docs
               (count
                 (scroll/hits
                   {:es-host    es-host
                    :index-name index-name}))))

        (is (<= number-of-docs (count (scroll/hits {:es-host es-host}))))))

    (testing "resuming scroll with search_after is not possible"
      (let [query {:sort ["_doc"]}
            records (scroll/hits
                      {:es-host    es-host
                       :index-name index-name
                       :query      query
                       :opts       {:size 1}})
            first-record (first records)]

        (is (vector? (:sort first-record)))

        (is (= 0 (count (scroll/hits
                          {:es-host    es-host
                           :index-name index-name
                           :query      (assoc query :search_after (:sort first-record))
                           :opts       {:size 1}}))))))

    (testing "if batch size is equal 0 then empty list of records should be returned with an error in the log"
      (is (= 0
             (count
               (scroll/hits
                 {:es-host    es-host
                  :index-name index-name
                  :opts       {:size 0}})))))

    ; TODO: test if search works (terms search with a couple of numbers)

    ;(testing "laziness: take 5 records sleep till scroll id expires; try to take all after sleep not fail in that"
    ;  (let [records (scroll/records
    ;                  {:es-host    es-host
    ;                   :index-name index-name
    ;                   :opts       {:size         1
    ;                                :keep-context "1s"}})]
    ;    (is (= 5 (count (take 5 records))))
    ;    (Thread/sleep 60000)
    ;    (is (= 5 (count records)))))

    (testing "various incomplete inputs"
      (try
        (scroll/hits
          {:es-host    nil
           :index-name index-name})
        (catch AssertionError e
          (is e)))
      (try
        (scroll/hits {})
        (catch AssertionError e
          (is e)))
      (try
        (scroll/hits nil)
        (catch AssertionError e
          (is e))))))