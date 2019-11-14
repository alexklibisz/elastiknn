#!/bin/bash
set -e

# Build the plugins.
cd $(dirname $0)/..
./gradlew assemble

# Build and start the containers.
cd testing
docker-compose up --detach --force-recreate --scale elasticsearch=3

# Healthcheck.
python3 -u - <<DOC
from urllib.request import Request, urlopen
import sys
from time import sleep
req = Request("http://localhost:9200/_cluster/health?wait_for_status=yellow&timeout=2s")
for _ in range(30):
  try:
    res = urlopen(req)
    if res.getcode() == 200:
      print("Elasticsearch is ready")
      sys.exit(0)
  except Exception:
    pass
  sys.stdout.write('.')
  sleep(1)
sys.stderr.write("Elasticsearch failed health checks")
sys.exit(1)
DOC