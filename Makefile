.PHONY: start-es
start-es:
	docker-compose -p es-kibana -f dockerfiles/docker-compose.es.yml up

run-dev-env: start-es

ES_TEST:=-p integration-tests -f dockerfiles/docker-compose.es.test.yml
.PHONY: run-integration-tests
run-integration-tests:
	docker-compose $(ES_TEST) pull
	docker-compose $(ES_TEST) down
	docker-compose $(ES_TEST) build
	docker-compose $(ES_TEST) up --remove-orphans --abort-on-container-exit --exit-code-from lib-test

.PHONY: release
release:
	rm release.properties || true
	rm pom.xml.releaseBackup || true
	clojure -Spom
	mvn release:prepare
