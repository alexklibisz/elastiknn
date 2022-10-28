import sys

print("|Model|Parameters|Recall|Queries per Second|")
print("|---|---|---|---|")

results = []

for line in sys.stdin:
  line = line.strip()
  if len(line) < 1:
    continue
  else:
    if line[0] in '0123456789':
      tokens = [t for t in line.split(' ') if len(t) > 0]
      description = tokens[1].replace('_', '-')
      model = '-'.join([t for t in description.split('-') if '=' not in t])
      parameters = ' '.join([t for t in description.split('-') if '=' in t])
      recall = tokens[2]
      qps = tokens[3]
      results.append((model, parameters, recall, qps))

results_sorted = sorted(results, key=lambda t: (t[0], t[2], t[3]))
for result in results_sorted:
  print(f"|{result[0]}|{result[1]}|{result[2]}|{result[3]}|")
