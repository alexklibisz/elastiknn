from urllib.request import Request, urlopen
import sys
from time import sleep
print("Waiting for elasticsearch health endpoint...")
req = Request("http://localhost:9200/_cluster/health?wait_for_status=yellow&timeout=1s")
for _ in range(30):
  try:
    res = urlopen(req)
    if res.getcode() == 200:
      print("Elasticsearch is ready")
      sys.exit(0)
  except ConnectionResetError as e:
    print('.', end = ''); sys.stdout.flush()
    pass
  sleep(1)
print("Elasticsearch failed health checks", sys.stderr)
sys.exit(1)