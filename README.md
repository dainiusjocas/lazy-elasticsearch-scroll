# lazy-elasticsearch-scroll

![Linting](https://github.com/dainiusjocas/lazy-elasticsearch-scroll/workflows/clj-kondo%20linting/badge.svg)
![Tests](https://github.com/dainiusjocas/lazy-elasticsearch-scroll/workflows/Tests/badge.svg)
[![Clojars Project](https://img.shields.io/clojars/v/lt.jocas/lazy-elasticsearch-scroll.svg)](https://clojars.org/lt.jocas/lazy-elasticsearch-scroll)

Clojure library to use the Elasticsearch Scroll API as a lazy sequence.

## Use Cases

The purpose of the library is to have an interface the consume **all** or some part the data from Elasticsearch. Why would you need to do that:

- One-off data transfer between Elasticsearch clusters (e.g. production -> staging);
- One-off query replay from Elasticsearch logs cluster with slow queries back to the production Elasticsearch cluster;
- If your enriched documents goes directly to the production Elasticsearch, and you want to play with the enriched data on your laptop.
- etc...

## Latest Version

The library is uploaded to [Clojars](https://clojars.org/lt.jocas/lazy-elasticsearch-scroll), so you can just: 
```clojure
{:deps {lazy-elasticsearch-scroll {:mvn/version "1.0.4"}}}
```

If you want to use code straight from Github then:
```clojure
{:deps {lazy-elasticsearch-scroll {:git/url "https://github.com/dainiusjocas/lazy-elasticsearch-scroll.git"
                                   :sha "b8a01341cfac4747274e3b49a169d3e54d1f1dfa"}}}
```

## Quickstart

```clojure
(require '[scroll :as scroll])

(scroll/hits
  {:es-host    "http://localhost:9200"
   :index-name ".kibana"
   :query      {:query {:match_all {}}}})
;; =>
({:_id "space:default",
  :_type "_doc",
  :_score 1.0,
  :_index ".kibana_1",
  :_source {:space {:description "This is your default space!",
                    :color "#00bfb3",
                    :name "Default",
                    :_reserved true,
                    :disabledFeatures []},
            :migrationVersion {:space "6.6.0"},
            :type "space",
            :references [],
            :updated_at "2020-02-12T14:16:18.621Z"}}
 {:_id "config:7.6.0",
  :_type "_doc",
  :_score 1.0,
  :_index ".kibana_1",
  :_source {:config {:buildNum 29000}, :type "config", :references [], :updated_at "2020-02-12T14:16:20.526Z"}})
```

## Examples

```clojure
;; Scroll through all the documents:
(scroll/hits {:es-host "http://localhost:9200"})

;; Fetch at most 10 docs:
(take 10 (scroll/hits
           {:es-host    "http://localhost:9200"
            :index-name ".kibana"
            :query      {:query {:match_all {}}}}))

;; Do not keywordize keys
(scroll/hits
  {:es-host    "http://localhost:9200"
   :opts       {:keywordize?  false}})
;; =>
({"_score" nil,
  "_type" "_doc",
  "sort" [0],
  "_source" {"space" {"disabledFeatures" [],
                      "name" "Default",
                      "_reserved" true,
                      "color" "#00bfb3",
                      "description" "This is your default space!"},
             "references" [],
             "updated_at" "2020-02-12T14:16:18.621Z",
             "type" "space",
             "migrationVersion" {"space" "6.6.0"}},
  "_id" "space:default",
  "_index" ".kibana_1"}
 {"_score" nil, "_type" "_doc", "sort" [0], "_source" {"value" 0}, "_id" "0", "_index" "scroll-test-index"})
```

## Supported Elasticsearch versions

- 7.6.x
- 7.5.x
- 6.8.x

## Development

Run the development environment `make run-dev-env`. This will start a `docker-compose` cluster with Elasticsearch
and Kibana on exposed ports `9200` and `5601` respectively.

Run integration tests locally `make run-integration-tests`. This will start a `docker-compose` in which the integration
tests will be run.

## License

Copyright &copy; 2020 [Dainius Jocas](https://www.jocas.lt).

Distributed under the The Apache License, Version 2.0.
