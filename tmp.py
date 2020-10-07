import sys

nn = []
with open(sys.argv[1]) as fp:
    for l in fp:
        if l.startswith("counter"):
            nn.append(float(l.replace("counter.numHits() = ", "")))

avg = sum(nn) / len(nn)
print(avg)
