import h5py
import sys
import json

fname = sys.argv[1]
attrs = json.loads(sys.argv[2])
fp = h5py.File(fname, 'w')
for k, v in attrs.items():
    fp.attrs[k] = v
fp.close()