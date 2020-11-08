import json
import gzip 
import array

def iter_products(fname):
  with gzip.open(fname, 'r') as g:
    for l in g:
      yield eval(l)

def iter_vectors(fname):
  with open(fname, 'rb') as f:
    while True:
      asin = f.read(10)
      if asin == '': break
      a = array.array('f')
      a.fromfile(f, 4096)
      yield (asin, a.tolist())
