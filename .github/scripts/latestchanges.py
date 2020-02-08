import sys
for l in sys.stdin:
    if l == '---\n': break
    sys.stdout.write(l)
