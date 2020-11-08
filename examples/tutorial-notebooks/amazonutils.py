from IPython.display import Image, display

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
      yield (asin.decode(), a.tolist())

def display_hits(res):
    print(f"Found {res['hits']['total']['value']} hits in {res['took']} ms")
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