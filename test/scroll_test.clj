(ns scroll-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [clojure.tools.logging :as log]
    [scroll :as scroll]
    [utils :as utils]))

(deftest ^:integration search-after-strategy
  (let [es-host (or (System/getenv "ES_HOST") "http://localhost:9200")
        index-name "scroll-test-index"
        number-of-docs (+ 10 (rand-int 0))
        records (map (fn [x] {:_id x
                              :_source {:value x}}) (range number-of-docs))]
    (log/infof "Deleted index='%s' at '%s': %s"
               index-name es-host (utils/delete-index es-host index-name))
    (log/infof "Created index='%s' at '%s': %s" index-name es-host (utils/create-index es-host index-name))
    (utils/fill-index es-host index-name records)

    (testing "if all scroll results are fetched"
      (let [query {:query {:match_all {}} :size 3}
            hits (scroll/hits
                   {:es-host    es-host
                    :index-name index-name
                    :query      query
                    :opts       {:keywordize? true
                                 :strategy :search-after}})]
        (is (= number-of-docs (count hits)))))

    (testing "query with :from param"
      (let [query {:query {:match_all {}} :size 3}
            hits (scroll/hits
                   {:es-host    es-host
                    :index-name index-name
                    :query      (assoc query :from 1)
                    :opts       {:keywordize? true
                                 :strategy    :search-after
                                 :time        10
                                 :max         11}})]
        (is (= number-of-docs (count hits)))))

    (testing "search after with index pattern"
      (let [query {:query {:match_all {}} :size 3}
            hits (scroll/hits
                   {:es-host    es-host
                    :index-name (str index-name "*")
                    :query      (assoc query :from 1)
                    :opts       {:keywordize? true
                                 :strategy    :search-after
                                 :time        10
                                 :max         11}})]
        (is (= number-of-docs (count hits)))))

    (testing "search after not keywordized"
      (let [query {:query {:match_all {}} :size 3}
            hits (scroll/hits
                   {:es-host    es-host
                    :index-name index-name
                    :query      query
                    :opts       {:keywordize? false
                                 :strategy    :search-after
                                 :time        10
                                 :max         11}})]
        (is (= number-of-docs (count hits)))))))

(deftest ^:integration basic-scroll
  (let [es-host (or (System/getenv "ES_HOST") "http://localhost:9200")
        index-name "scroll-test-index"
        number-of-docs (+ 10 (rand-int 50))
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

    (testing "if query limits the number of items returned"
      (let [num-of-docs (rand-int number-of-docs)]
        (is (= num-of-docs (count (scroll/hits
                                    {:es-host    es-host
                                     :index-name index-name
                                     :query      {:query {:terms {:value (range num-of-docs)}}}}))))))

    (testing "if aggs dissoc works as expected"
      (let [num-of-docs (rand-int number-of-docs)]
        (is (= num-of-docs (count (scroll/hits
                                    {:es-host    es-host
                                     :index-name index-name
                                     :query      {:query {:terms {:value (range num-of-docs)}}
                                                  :aggs {:agg-name {:terms {:field :value :size 10}}}}}))))
        (is (= num-of-docs (count (scroll/hits
                                    {:es-host    es-host
                                     :index-name index-name
                                     :query      {:query {:terms {:value (range num-of-docs)}}
                                                  "aggs" {:agg-name {:terms {:field :value :size 10}}}}}))))
        (is (= num-of-docs (count (scroll/hits
                                    {:es-host    es-host
                                     :index-name index-name
                                     :opts       {:preserve-aggs? true}
                                     :query      {:query {:terms {:value (range num-of-docs)}}
                                                  "aggs" {:agg-name {:terms {:field :value :size 10}}}}}))))))

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
                           :opts       {:size 1
                                        :time 10
                                        :max  11}}))))))

    (testing "if batch size is equal 0 then empty list of records should be returned with an error in the log"
      (is (= 0
             (count
               (scroll/hits
                 {:es-host    es-host
                  :index-name index-name
                  :opts       {:size 0
                               :time 10
                               :max  11}})))))

    (testing "laziness: take 5 records sleep till scroll id expires; try to take all after sleep not fail in that"
      (let [records (scroll/hits
                      {:es-host    es-host
                       :index-name index-name
                       :opts       {:size         5
                                    :keep-context "1ms"
                                    :time 10
                                    :max  11}})]
        (is (= 5 (count (take 5 records))))
        ; Auto scroll context deletion is an async op, it might take up to a minute.
        ; By deleting all scroll contexts we simulate expiration, instead of sleeping.
        (utils/delete-all-scroll-contexts es-host)
        (is (= 5 (count records)))))

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
