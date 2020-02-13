.PHONY: start-es-760
start-es-760:
	docker-compose -p es-kibana-760 -f dockerfiles/docker-compose.es-760.yml up

run-dev-env: start-es-760

ES_7_TEST:=-p integration-tests -f dockerfiles/docker-compose.es-760.test.yml
.PHONY: run-integration-tests
run-integration-tests:
	docker-compose $(ES_7_TEST) pull
	docker-compose $(ES_7_TEST) down
	docker-compose $(ES_7_TEST) build
	docker-compose $(ES_7_TEST) up --remove-orphans --abort-on-container-exit --exit-code-from lib-test
