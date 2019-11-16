#!/bin/bash
set -e

# Prevent this error: max virtual memory areas vm.max_map_count [65530] is too low, increase to at least [262144]
sudo sysctl -w vm.max_map_count=262144

# Build the plugins.
cd $(dirname $0)/..
./gradlew clean assemble

# Build and start the containers.
cd testing
docker-compose up --detach --build --force-recreate --scale elasticsearch_data=2

# Healthcheck.
python3 -u - <<DOC
from urllib.request import Request, urlopen
import sys
from time import sleep
print("Running healthcheck")
req = Request("http://localhost:9200/_cluster/health?wait_for_status=yellow&timeout=1s")
for _ in range(30):
  try:
    res = urlopen(req)
    if res.getcode() == 200:
      print("Elasticsearch is ready")
      sys.exit(0)
  except ConnectionResetError as e:
    sys.stdout.write('.')
    pass
  sleep(1)
print("Elasticsearch failed health checks", sys.stderr)
sys.exit(1)
DOC