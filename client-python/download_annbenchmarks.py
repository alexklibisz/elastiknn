import json
import os
import sys
import urllib.request
import h5py

from elastiknn.utils import canonical_vectors_to_elastiknn
from gen_test_data import Query, TestData

datasets = sys.argv[1:]
assert len(datasets) > 0, "Must provide one or more dataset names (e.g. kosarak-jaccard, mnist-784-euclidean, etc.)"

basedir = os.path.expanduser("~/.ann-benchmarks")
if not os.path.exists(basedir):
    os.mkdir(basedir)

for dsname in datasets:
    srcpath = os.path.join(basedir, f"{dsname}.hdf5")
    if not os.path.exists(srcpath):
        url = f"http://ann-benchmarks.com/{dsname}.hdf5"
        print(f"Downloading {url} to {srcpath}")
        urllib.request.urlretrieve(url, srcpath)
    hf = h5py.File(srcpath, 'r')

    train = canonical_vectors_to_elastiknn(hf['train'][:, :])
    test = canonical_vectors_to_elastiknn(hf['test'][:, :])
    queries = [
        Query(vector=t, similarities=list(d), indices=list(n))
        for (t, d, n) in zip(test, hf['distances'], hf['neighbors'])
    ]
    test_data = TestData(corpus=list(train), queries=queries)

    outpath = os.path.join(basedir, f"{dsname}.json")
    print(f"Saving test data to {outpath}")
    with open(outpath, "w") as fp:
        json.dump(test_data.to_dict(), fp)
