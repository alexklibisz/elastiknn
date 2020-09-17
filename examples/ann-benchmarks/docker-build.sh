# Use this script in the ann-benchmarks directory to build the elastiknn docker image.
#!/bin/bash
set -e
docker build -t ann-benchmarks -f install/Dockerfile .
docker build -t ann-benchmarks-elastiknn -f install/Dockerfile.elastiknn .
docker run -v $(pwd)/ann_benchmarks:/home/app/ann_benchmarks --rm ann-benchmarks-elastiknn --help
