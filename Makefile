
pwd = $(shell pwd)
core = $(pwd)/core
vpip = ./venv/bin/pip
vpy = ./venv/bin/python
gradle = ./gradlew
eslatest = elastiknn-plugin
s3 = aws s3
build_bucket = s3://com-klibisz-elastiknn-builds/
dc = docker-compose
version = $(shell cat version)
git_branch = $(shell git rev-parse --abbrev-ref HEAD)
src_all = $(shell git diff --name-only --diff-filter=ACMR)
site_srvr = elastiknn-site
site_main = elastiknn.klibisz.com
site_arch = archive.elastiknn.klibisz.com
ecr_prefix = ${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_DEFAULT_REGION}.amazonaws.com
ecr_benchmarks_prefix = $(ecr_prefix)/elastiknn-benchmarks-cluster

clean:
	./gradlew clean
	cd elastiknn-testing && $(dc) down
	rm -rf .mk/*

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

.mk/client-python-install: .mk/client-python-venv client-python/requirements*.txt
	cd client-python \
		&& $(vpip) install -q -r requirements.txt \
		&& $(vpip) install -q -r requirements-build.txt
	touch $@

.mk/gradle-compile: $(src_all)
	$(gradle) --console=plain compileScala compileJava compileTestScala compileTestJava
	touch $@

.mk/gradle-publish-local: version $(src_all)
	$(gradle) assemble publishToMavenLocal
	touch $@

.mk/client-python-publish-local: version .mk/client-python-install
	cd client-python && rm -rf dist && $(vpy) setup.py sdist bdist_wheel && ls dist
	touch $@

.mk/vm-max-map-count:
	sudo sysctl -w vm.max_map_count=262144

.mk/run-cluster: .mk/python3-installed .mk/docker-compose-installed .mk/gradle-publish-local .mk/vm-max-map-count
	cd elastiknn-testing \
	&& $(dc) down \
	&& $(dc) up --detach --build --force-recreate --scale elasticsearch_data=1 \
	&& python3 cluster_ready.py
	touch $@

.mk/example-scala-sbt-client-usage: .mk/gradle-publish-local
	cd examples/scala-sbt-client-usage && sbt ";clean;run"
	touch $@

.mk/example-demo-sbt-docker-stage: .mk/gradle-publish-local $(src_all)
	cd examples/demo/webapp && sbt docker:stage
	touch $@

compile/gradle: .mk/gradle-compile

compile: compile/gradle

run/cluster: .mk/run-cluster

run/gradle:
	cd elastiknn-testing && $(dc) down
	$(gradle) :plugin:run $(shell cat .esopts | xargs)

run/debug:
	cd elastiknn-testing && $(dc) down
	$(gradle) :plugin:run $(shell cat .esopts | xargs) --debug-jvm

run/kibana:
	docker run --network host -e ELASTICSEARCH_HOSTS=http://localhost:9200 -p 5601:5601 -d --rm kibana:7.6.2
	docker ps | grep kibana

run/demo: .mk/gradle-publish-local .mk/example-demo-sbt-docker-stage .mk/example-demo-sbt-docker-stage .mk/vm-max-map-count
	cd examples/demo && \
	PLAY_HTTP_SECRET_KEY=$(shell sha256sum ~/.ssh/id_rsa | cut -d' ' -f1) $(dc) up --build --detach

test/python: .mk/client-python-install
	cd client-python && $(vpy) -m pytest

test/gradle:
	$(gradle) test

test: clean run/cluster
	$(MAKE) test/gradle
	$(MAKE) test/python

examples: .mk/example-scala-sbt-client-usage .mk/example-demo-sbt-docker-stage

publish/local: .mk/gradle-publish-local .mk/client-python-publish-local

publish/snapshot/sonatype: .mk/gradle-publish-local
	$(gradle) publish

publish/snapshot/plugin: .mk/gradle-publish-local
	hub release delete $(version) || true
	hub release create -p -m $(version) -a $(eslatest)/build/distributions/elastiknn-$(version)*.zip $(version)

publish/release/sonatype: .mk/gradle-publish-local
	SONATYPE_URL="https://oss.sonatype.org/service/local/staging/deploy/maven2" $(gradle) publish

publish/release/plugin: .mk/gradle-publish-local
	cp version release.md
	echo "" >> release.md
	cat changelog.md | python .github/scripts/latestchanges.py >> release.md
	hub release create -p -F release.md -a $(eslatest)/build/distributions/elastiknn-$(version)*.zip $(version)

publish/snapshot/python: .mk/client-python-publish-local
	cd client-python \
		&& $(vpy) -m twine upload -r pypi --verbose dist/*

publish/release/python: .mk/client-python-publish-local
	cd client-python \
		&& $(vpy) -m twine upload -r pypi --verbose dist/*

.mk/gradle-docs: $(src_all)
	$(gradle) unifiedScaladocs
	touch $@

.mk/client-python-docs: $(src_all) .mk/client-python-install
	cd client-python \
		&& rm -rf pdoc \
		&& venv/bin/pdoc3 --html elastiknn -c show_type_annotations=True -o pdoc
	touch $@

.mk/jekyll-site-build: docs/**/*
	cd docs && bundle install && bundle exec jekyll build
	touch $@

run/docs:
	cd docs && bundle exec jekyll serve

compile/docs: .mk/gradle-docs .mk/client-python-docs

compile/site: .mk/jekyll-site-build

publish/docs: .mk/gradle-docs .mk/client-python-docs
	ssh $(site_srvr) mkdir -p $(site_arch)/$(version)
	rsync -av --delete build/docs/scaladoc $(site_srvr):$(site_arch)/$(version)
	rsync -av --delete client-python/pdoc/elastiknn/ $(site_srvr):$(site_arch)/$(version)/pdoc
	
	ssh $(site_srvr) mkdir -p $(site_main)/docs
	rsync -av --delete build/docs/scaladoc $(site_srvr):$(site_main)/docs
	rsync -av --delete client-python/pdoc/elastiknn/ $(site_srvr):$(site_main)/docs/pdoc

publish/site: .mk/jekyll-site-build
	mkdir -p docs/_site/docs
	rsync -av --delete --exclude docs docs/_site/ $(site_srvr):$(site_main)

.mk/benchmarks-assemble: $(src_all)
	$(gradle) :benchmarks:shadowJar
	touch $@

.mk/benchmarks-docker-build: .mk/benchmarks-assemble .mk/gradle-publish-local
	cd plugin && docker build -t $(ecr_benchmarks_prefix).elastiknn .
	cd benchmarks && docker build -t $(ecr_benchmarks_prefix).driver .
	cd benchmarks/python \
		&& (ls venv || python3 -m virtualenv venv) \
		&& venv/bin/pip install -r requirements.txt \
		&& docker build -t $(ecr_benchmarks_prefix).datasets .
	touch $@

.mk/benchmarks-docker-push: benchmarks/docker/login benchmarks/docker/build
	docker push $(ecr_benchmarks_prefix).elastiknn
	docker push $(ecr_benchmarks_prefix).driver
	docker push $(ecr_benchmarks_prefix).datasets
	touch $@

benchmarks/continuous/trigger:
	curl -H "Accept: application/vnd.github.everest-preview+json" \
		 -H "Authorization: token ${GITHUB_TOKEN}" \
		 --request POST \
		 --data '{"event_type": "benchmark", "client_payload": { "branch": "'$(git_branch)'"}}' \
		 https://api.github.com/repos/alexklibisz/elastiknn/dispatches

benchmarks/continuous/run: benchmarks/minio
	mv .minio elastiknn-benchmarks/.minio || true \
		&& $(gradle) --console=plain -PmainClass=com.klibisz.elastiknn.benchmarks.ContinuousBenchmark :benchmarks:run

benchmarks/docker/login:
	$$(aws ecr get-login --no-include-email)

benchmarks/docker/build: .mk/benchmarks-docker-build

benchmarks/docker/push: .mk/benchmarks-docker-push

benchmarks/minio:
	cd elastiknn-testing && docker-compose up -d minio

benchmarks/argo/submit/benchmarks: .mk/benchmarks-docker-push
	cd benchmarks/deploy \
	&& envsubst < benchmark-workflow.yaml | argo submit -

benchmarks/argo/submit/datasets: .mk/benchmarks-docker-push
	cd benchmarks/deploy \
	&& envsubst < datasets-workflow.yaml | argo submit -

benchmarks/adhoc:
	cd benchmarks/deploy \
	&& envsubst < adhoc-pod.yaml | kubectl apply -f -
