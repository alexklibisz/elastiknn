
src_all = $(shell git ls-files | sed 's/\ /\\ /')

clean:
	rm -rf .mk/*

.mk/python3-installed:
	python3 --version > /dev/null
	python3 -m pip install -q virtualenv
	touch .mk/python3-installed

.mk/client-python-venv: .mk/python3-installed
	cd client-python && python3 -m virtualenv venv
	touch .mk/client-python-venv

.mk/client-python-install: .mk/client-python-venv
	cd client-python \
		&& ./venv/bin/pip install -q -r requirements.txt \
		&& ./venv/bin/pip install -q grpcio-tools
	touch .mk/client-python-install

.mk/gradle-assemble: $(src_all)
	./gradlew clean assemble
	touch .mk/gradle-assemble

.mk/client-python-compile: .mk/client-python-install .mk/gradle-assemble
	cd client-python \
		&& ./venv/bin/python -m grpc_tools.protoc \
			--proto_path=../core/src/main/proto \
			--proto_path=../core/build/extracted-include-protos/main \
			--python_out=. \



	touch .mk/client-python-compile

.mk/client-python-publish-s3:
	touch .mk/client-python-publish-s3
