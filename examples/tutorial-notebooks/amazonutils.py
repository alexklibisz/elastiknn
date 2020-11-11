from IPython.display import Image, display
from itertools import islice
import numpy as np
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
      try:
        asin = f.read(10)
        a = array.array('f')
        a.fromfile(f, 4096)
        yield (asin.decode(), a.tolist())
      except EOFError:
        break

def iter_vectors_reduced(fname, dims=1024, samples=10000):
  sumarr = np.zeros(4096) * 1.0
  for (_, v) in islice(iter_vectors(fname), samples):
    sumarr -= np.array(v)
  ii = np.argsort(sumarr)[:dims]

  def f(fname):
    for (asin, vec) in iter_vectors(fname):
      yield (asin, np.array(vec)[ii].tolist())

  return f

def display_hits(res):
    print(f"Found {res['hits']['total']['value']} hits in {res['took']} ms. Showing top {len(res['hits']['hits'])}.")
    print("")
    for hit in res['hits']['hits']:
        s = hit['_source']    
        print(f"Title:          {s.get('title', None)}")
        print(f"Description:    {s.get('description', None)}"[:100] + "...")
        print(f"Price:          {s.get('price', None)}")
        print(f"ID:             {s.get('asin', None)}")
        print(f"Score:          {hit.get('_score', None)}")

        display(Image(s.get("imUrl"), width=128))
        print("")