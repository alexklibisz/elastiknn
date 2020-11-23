
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
site_main = elastiknn.com
site_arch = archive.elastiknn.com
ecr_prefix = ${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_DEFAULT_REGION}.amazonaws.com
ecr_benchmarks_prefix = $(ecr_prefix)/elastiknn-benchmarks-cluster

.mk/benchmarks-assemble: $(src_all)
	$(gradle) :benchmarks:shadowJar
	touch $@

.mk/benchmarks-docker-build: .mk/benchmarks-assemble .mk/gradle-publish-local
	cd elastiknn-plugin && docker build -t $(ecr_benchmarks_prefix).elastiknn .
	cd elastiknn-benchmarks && docker build -t $(ecr_benchmarks_prefix).driver .
	cd elastiknn-benchmarks/python \
	&& (ls venv || python3 -m venv venv) \
	&& ./venv/bin/pip install -r requirements.txt \
	&& docker build -t $(ecr_benchmarks_prefix).datasets .
	touch $@

.mk/benchmarks-docker-push: benchmarks/docker/login benchmarks/docker/build
	docker push $(ecr_benchmarks_prefix).elastiknn
	docker push $(ecr_benchmarks_prefix).driver
	docker push $(ecr_benchmarks_prefix).datasets
	touch $@

benchmarks/continuous/run:
	$(gradle) --console=plain -PmainClass=com.klibisz.elastiknn.benchmarks.ContinuousBenchmark :benchmarks:run

benchmarks/docker/login:
	$$(aws ecr get-login --no-include-email)

benchmarks/docker/build: .mk/benchmarks-docker-build

benchmarks/docker/push: .mk/benchmarks-docker-push

benchmarks/minio:
	cd elastiknn-testing && docker-compose up -d minio

benchmarks/argo/submit/benchmark: .mk/benchmarks-docker-push
	cd elastiknn-benchmarks/deploy \
	&& envsubst < templates/benchmark-workflow-params.yaml > /tmp/params.yaml \
	&& argo submit benchmark-workflow.yaml --parameter-file /tmp/params.yaml

benchmarks/argo/submit/datasets: .mk/benchmarks-docker-push
	cd elastiknn-benchmarks/deploy \
	&& envsubst < datasets-workflow.yaml | argo submit -

benchmarks/adhoc:
	cd elastiknn-benchmarks/deploy \
	&& envsubst < adhoc-pod.yaml | kubectl apply -f -



