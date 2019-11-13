#!/bin/bash
set -e

docker-compose up -d --scale elasticsearch=3

python2 -u - <<DOC
import urllib2
import sys
from time import sleep
req = urllib2.Request("http://localhost:9200/_cluster/health?wait_for_status=yellow&timeout=2s")
for i in range(30):
  try:
    res = urllib2.urlopen(req)
    if res.getcode() == 200:
      sys.stdout.write("Elasticsearch is ready\n")
      sys.exit(0)
  except Exception:
    pass
  sys.stdout.write('. ')
  sleep(1)
sys.stderr.write("Elasticsearch failed health checks")
sys.exit(1)
DOC