
pwd = $(shell pwd)
core = $(pwd)/core
vpip = ./venv/bin/pip
vpy = ./venv/bin/python
gradle = ./gradlew
eslatest = es74x
s3 = aws s3
build_bucket = s3://com-klibisz-elastiknn-builds/
dc = docker-compose
src_all = $(shell git diff --name-only --diff-filter=ACMR)

clean:
	./gradlew clean
	cd testing && $(dc) down
	rm -rf .mk/*

.mk/sudo:
	sudo -v

.mk/python3-installed:
	python3 --version > /dev/null
	python3 -m pip install -q virtualenv
	touch $@

.mk/docker-compose-installed:
	docker-compose --version > /dev/null
	touch $@

.mk/client-python-venv: .mk/python3-installed
	cd client-python && python3 -m virtualenv venv
	touch $@

.mk/client-python-install: .mk/client-python-venv
	cd client-python \
		&& $(vpip) install -q -r requirements.txt \
		&& $(vpip) install -q grpcio-tools pytest mypy-protobuf
	touch $@

.mk/gradle-compile: $(src_all)
	$(gradle) compileScala compileJava compileTestScala compileTestJava
	touch $@

.mk/gradle-gen-proto: $(src_all)
	$(gradle) generateProto
	touch $@

.mk/gradle-publish-local: $(src_all)
	$(gradle) assemble publishToMavenLocal
	touch $@

.mk/client-python-compile: .mk/client-python-install .mk/gradle-gen-proto
	cd client-python \
		&& $(vpy) -m grpc_tools.protoc \
			--proto_path=$(core)/src/main/proto \
			--proto_path=$(core)/build/extracted-include-protos/main \
			--python_out=. \
			--plugin=protoc-gen-mypy=venv/bin/protoc-gen-mypy \
			--mypy_out=. \
			$(core)/src/main/proto/elastiknn/elastiknn.proto \
			$(core)/build/extracted-include-protos/main/scalapb/scalapb.proto \
		&& $(vpy) -c "from elastiknn.elastiknn_pb2 import Similarity; x = Similarity.values()"
	touch $@

.mk/client-python-publish-local: .mk/client-python-compile
	cd client-python && $(vpy) setup.py bdist_wheel && ls dist
	touch $@

.mk/publish-s3: .mk/gradle-publish-local .mk/client-python-publish-local
	aws s3 sync $(eslatest)/build/distributions $(build_bucket)
	aws s3 sync client-python/dist $(build_bucket)
	aws s3 ls $(build_bucket)
	touch $@

.mk/run-cluster: .mk/sudo .mk/python3-installed .mk/docker-compose-installed .mk/gradle-publish-local
	sudo sysctl -w vm.max_map_count=262144
	cd testing \
		&& $(dc) down \
		&& $(dc) up --detach --build --force-recreate --scale elasticsearch_data=2 \
		&& python3 cluster_ready.py
	touch $@

.mk/example-scala-sbt-client-usage: $(src_all)
	cd examples/scala-sbt-client-usage && sbt run
	touch $@

compile/gradle: .mk/gradle-compile

compile/python: .mk/client-python-compile

compile: compile/gradle compile/python

run/cluster: .mk/run-cluster

run/gradle:
	cd testing && $(dc) down
	$(gradle) clean run -Dtests.heap.size=2G # TODO: Can you set tests.heap.size in build.gradle?

run/debug:
	cd testing && $(dc) down
	$(gradle) clean run --debug-jvm

run/kibana:
	docker run --network host -e ELASTICSEARCH_HOSTS=http://localhost:9200 -p 5601:5601 -d --rm kibana:7.4.0
	docker ps | grep kibana

test/python:
	cd client-python && $(vpy) -m pytest

test/gradle:
	$(gradle) test

test: clean compile/python run/cluster
	$(MAKE) test/gradle
	$(MAKE) test/python

examples: .mk/example-scala-sbt-client-usage

publish/local: .mk/gradle-publish-local .mk/client-python-publish-local

publish/s3: .mk/publish-s3
