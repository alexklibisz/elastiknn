
pwd = $(shell pwd)
core = $(pwd)/core
vpip = ./venv/bin/pip
vpy = ./venv/bin/python
gradle = ./gradlew
eslatest = es74x
s3 = aws s3
build_bucket = s3://com-klibisz-elastiknn-builds/
dc = docker-compose
version = $(shell cat version)
src_all = $(shell git diff --name-only --diff-filter=ACMR)
site_srvr = elastiknn-site
site_main = elastiknn.klibisz.com
site_arch = archive.elastiknn.klibisz.com

clean:
	./gradlew clean
	cd testing && $(dc) down
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
	$(gradle) compileScala compileJava compileTestScala compileTestJava
	touch $@

.mk/gradle-publish-local: version $(src_all)
	$(gradle) assemble publishToMavenLocal
	touch $@

.mk/client-python-publish-local: version .mk/client-python-install
	cd client-python && rm -rf dist && $(vpy) setup.py sdist bdist_wheel && ls dist
	touch $@

.mk/run-cluster: .mk/python3-installed .mk/docker-compose-installed .mk/gradle-publish-local
	sudo sysctl -w vm.max_map_count=262144
	cd testing \
	&& $(dc) down \
	&& $(dc) up --detach --build --force-recreate --scale elasticsearch_data=2 \
	&& python3 cluster_ready.py
	touch $@

.mk/example-scala-sbt-client-usage: .mk/gradle-publish-local
	cd examples/scala-sbt-client-usage && sbt run
	touch $@

compile/gradle: .mk/gradle-compile

compile: compile/gradle

run/cluster: .mk/run-cluster

run/gradle:
	cd testing && $(dc) down
	$(gradle) run $(shell cat .esopts | xargs)

run/debug:
	cd testing && $(dc) down
	$(gradle) run $(shell cat .esopts | xargs) --debug-jvm

run/kibana:
	docker run --network host -e ELASTICSEARCH_HOSTS=http://localhost:9200 -p 5601:5601 -d --rm kibana:7.4.0
	docker ps | grep kibana

test/python: .mk/client-python-install
	cd client-python && $(vpy) -m pytest

test/gradle:
	$(gradle) test

test: clean run/cluster
	$(MAKE) test/gradle
	$(MAKE) test/python

examples: .mk/example-scala-sbt-client-usage

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
	&& $(vpy) -m twine upload -r pypi dist/*

publish/release/python: .mk/client-python-publish-local
	cd client-python \
	&& $(vpy) -m twine upload -r pypi dist/*

.mk/gradle-docs: $(src_all)
	$(gradle) unifiedScaladocs
	touch $@

.mk/client-python-docs: $(src_all) .mk/client-python-install
	cd client-python \
	&& rm -rf pdoc \
	&& venv/bin/pdoc3 --html elastiknn -c show_type_annotations=True -o pdoc
	touch $@

.mk/jekyll-site-build: docs/**/* .mk/gradle-docs .mk/client-python-docs
	cd docs && bundle install && bundle exec jekyll build
	mkdir -p docs/_site/docs
	rsync -av --delete build/docs/scaladoc docs/_site/docs
	rsync -av --delete client-python/pdoc/elastiknn/ docs/_site/docs/pdoc
	touch $@

compile/docs: .mk/gradle-docs .mk/client-python-docs

compile/site: .mk/jekyll-site-build

publish/docs: .mk/gradle-docs .mk/client-python-docs
	ssh $(site_srvr) mkdir -p $(site_arch)/$(version)
	rsync -av --delete build/docs/scaladoc $(site_srvr):$(site_arch)/$(version)
	rsync -av --delete client-python/pdoc/elastiknn/ $(site_srvr):$(site_arch)/$(version)/pdoc
	
publish/site: .mk/jekyll-site-build
	rsync -av --delete docs/_site/ $(site_srvr):$(site_main)
	