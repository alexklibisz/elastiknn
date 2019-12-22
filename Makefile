
pwd = $(shell pwd)
core = $(pwd)/core
vpip = ./venv/bin/pip
vpy = ./venv/bin/python
gradle = ./gradlew
eslatest = es74x
s3 = aws s3
build_bucket = s3://com-klibisz-elastiknn-builds/
src_all = $(shell git ls-files | sed 's/\ /\\ /')

clean:
	./gradlew clean
	rm -rf .mk/*

.mk/python3-installed:
	python3 --version > /dev/null
	python3 -m pip install -q virtualenv
	touch $@

.mk/client-python-venv: .mk/python3-installed
	cd client-python && python3 -m virtualenv venv
	touch $@

.mk/client-python-install: .mk/client-python-venv
	cd client-python \
		&& $(vpip) install -q -r requirements.txt \
		&& $(vpip) install -q grpcio-tools
	touch $@

.mk/gradle-gen-proto: $(src_all)
	$(gradle) generateProto
	touch $@

.mk/gradle-publish-local: $(src_all)
	$(gradle) assemble
	touch $@

.mk/client-python-compile: .mk/client-python-install .mk/gradle-gen-proto
	cd client-python \
		&& $(vpy) -m grpc_tools.protoc \
			--proto_path=$(core)/src/main/proto \
			--proto_path=$(core)/build/extracted-include-protos/main \
			--python_out=. \
			$(core)/src/main/proto/elastiknn/elastiknn.proto $(core)/build/extracted-include-protos/main/scalapb/scalapb.proto \
		&& $(vpy) -c "from elastiknn.elastiknn_pb2 import Similarity; x = Similarity.values()"
	touch $@

.mk/client-python-publish-local: .mk/client-python-compile
	cd client-python && $(vpy) setup.py sdist && ls dist
	touch $@

.mk/client-python-publish-s3: .mk/gradle-publish-local .mk/client-python-publish-local
	aws s3 sync $(eslatest)/build/distributions $(build_bucket)
	aws s3 sync client-python/dist $(build_bucket)
	aws s3 ls $(build_bucket)
	touch $@

