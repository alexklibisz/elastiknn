from pprint import pprint
from time import time
import gensim.downloader as gensimdl

t0 = time()
ds = gensimdl.load('word2vec-google-news-300')
# ds = gensimdl.load('glove-wiki-gigaword-50')
t1 = time()
dur = t1 - t0
pprint(ds)