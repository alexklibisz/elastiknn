from pprint import pprint

import gensim.downloader as gensimdl

ds = gensimdl.load('word2vec-google-news-300')
print(ds)

ds = gensimdl.load('glove-wiki-gigaword-50')
print(ds)