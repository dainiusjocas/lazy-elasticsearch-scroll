# lazy-elasticsearch-scroll

![Linting](https://github.com/dainiusjocas/lazy-elasticsearch-scroll/workflows/clj-kondo%20linting/badge.svg)
![Tests](https://github.com/dainiusjocas/lazy-elasticsearch-scroll/workflows/Tests/badge.svg)
[![Clojars Project](https://img.shields.io/clojars/v/lt.jocas/lazy-elasticsearch-scroll.svg)](https://clojars.org/lt.jocas/lazy-elasticsearch-scroll)
[![cljdoc badge](https://cljdoc.org/badge/lt.jocas/lazy-elasticsearch-scroll)](https://cljdoc.org/d/lt.jocas/lazy-elasticsearch-scroll/CURRENT)

Clojure library to get the data from Elasticsearch as a lazy sequence. Following strategies are supported:
- [Scroll API](https://www.elastic.co/guide/en/elasticsearch/reference/current/search-request-body.html#request-body-search-scroll)
- [`search_after`](https://www.elastic.co/guide/en/elasticsearch/reference/current/search-request-body.html#request-body-search-search-after)
- TODO: sliced scroll

The scroll API is the default strategy.

## Use Cases

The purpose of the library is to have an interface the consume **all** or some part the data from Elasticsearch. Why would you need to do that:

- You need more documents than `index.max_result_window`;
- One-off data transfer between Elasticsearch clusters (e.g. production -> staging);
- One-off query replay from Elasticsearch logs cluster with slow queries back to the production Elasticsearch cluster;
- If your enriched documents goes directly to the production Elasticsearch, and you want to play with the enriched data on your laptop;
- etc...

## Latest Version

The library is uploaded to [Clojars](https://clojars.org/lt.jocas/lazy-elasticsearch-scroll), so you can just: 
```clojure
{:deps {lt.jocas/lazy-elasticsearch-scroll {:mvn/version "1.0.13"}}}
```

If you want to use the code straight from Github then:
```clojure
{:deps {lt.jocas/lazy-elasticsearch-scroll {:git/url "https://github.com/dainiusjocas/lazy-elasticsearch-scroll.git"
                                            :sha "c8e1bfc4564af51085db1e50b3427236c2f433c0"}}}
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

## Using strategies

To specify strategy you need to pass one of the following keys in the opts map: `[:scroll-api :search-after]`. For the scroll API:

```clojure
(scroll/hits
  {:es-host    "http://localhost:9200"
   :opts       {:strategy     :scroll-api}})
```

For the `search_after`:
```clojure
(scroll/hits
  {:es-host    "http://localhost:9200"
   :opts       {:strategy     :search-after}})
```

## Compare strategies

The scroll API is the default choice because it is the most common and relatively convenient way of getting documents from Elasticsearch. However, it has several disadvantages:
- it creates state in the cluster (what if only GET requests are allowed in your environment?);
- might get resource intensive;
- if your downstream consumers are not fast enough then scroll context might expire and then you need to start over;

The `search-after` strategy has several nice benefits:
- it is stateless (no such thing as expired contexts);
- `search_after` under the hood is filtering, filters can be cached, so it is reasonably fast;
- uses the standard search API.

However `search-after` [is not a silver bullet](https://github.com/elastic/elasticsearch/issues/16631):
- slower then scrolling;
- no point in time snapshot of data (might get some data multiple times);
- it requires thinking which attributes to use for sorting as tiebreaker;
    - sorting on `_id` is resource intensive, therefore you might get timeouts;
    - sorting on `_doc` is unpredictable because _doc is unique per shard;
- how to parallelize fetching?

## User Authorization

The basic authorization is supported via environment variables:

- `ELASTIC_USERNAME`, no default value
- `ELASTIC_PASSWORD`, no default value

## Supported Elasticsearch versions

- 7.6.x
- 6.8.x
- 5.6.X

## Development

Run the development environment `make run-dev-env`. This will start a `docker-compose` cluster with Elasticsearch
and Kibana on exposed ports `9200` and `5601` respectively.

To run development environment with a specific ELK version:
```shell script
(export ES_VERSION=6.8.8 && make run-dev-env)
```

Run integration tests locally `make run-integration-tests`. This will start a `docker-compose` in which the integration
tests will be run.

To run integration tests with a specific ELK version:

```shell script
(export ES_VERSION=6.8.8 && make run-integration-tests)
```

## License

Copyright &copy; 2020 [Dainius Jocas](https://www.jocas.lt).

Distributed under the The Apache License, Version 2.0.
