from urllib.request import Request, urlopen
import sys
from time import sleep
sys.stdout.write("Waiting for elasticsearch health endpoint")
req = Request("http://localhost:9200/_cluster/health?wait_for_status=yellow&timeout=1s")
for i in range(1, 181):
  try:
    res = urlopen(req)
    if res.getcode() == 200:
      print("\nElasticsearch is ready")
      sys.exit(0)
  except (Exception) as e:
    print('.', end='')
    sys.stdout.flush()
    pass
  sleep(1)
print("Elasticsearch failed health checks", sys.stderr)
sys.exit(1)
