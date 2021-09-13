import json, h5py, sys, time

fname = sys.argv[1]
attrs = json.loads(sys.argv[2])
fp = h5py.File(fname, 'w')
for k, v in attrs.items():
    fp.attrs.create(k, v)
time.sleep(1)
fp.close()
