import json
import os
import sys
from base64 import b64encode
from io import BytesIO
from pprint import pprint
from typing import Any

from PIL import Image
from dataclasses import dataclass
from dataclasses_json import dataclass_json, LetterCase
from elasticsearch import Elasticsearch
from elasticsearch.helpers import bulk
from elastiknn.utils import *
from keras.datasets import cifar100, cifar10, mnist
import gensim.downloader as gensimdl
from requests import get

@dataclass_json
@dataclass
class Example:
    name: str
    index: str
    field: str
    mapping: str
    query: Any


@dataclass_json(letter_case=LetterCase.CAMEL)
@dataclass
class Dataset:
    source_name: str
    pretty_name: str
    permalink: str
    examples: List[Example]


def load_docs(name: str):

    if name in {"mnist", "mnist_binary"}:
        (xtrn, _), (xtst, _) = mnist.load_data()
        for imgs in [xtrn, xtst]:
            for img in imgs:
                jpg = Image.fromarray(img)
                buf = BytesIO()
                jpg.save(buf, format="JPEG")
                b64 = b64encode(buf.getvalue()).decode()
                [vec] = ndarray_to_sparse_bool_vectors(img.reshape((1, np.product(img.shape))) / 256.0 > 0)
                yield dict(vec=vec.to_dict(), b64=b64)

    elif name == "cifar":
        (xtrn10, _), (xtst10, _) = cifar10.load_data()
        (xtrn100, _), (xtst100, _) = cifar100.load_data(label_mode='fine')
        for imgs in [xtrn10, xtst10, xtrn100, xtst100]:
            for img in imgs:
                jpg = Image.fromarray(img)
                buf = BytesIO()
                jpg.save(buf, format="JPEG")
                b64 = b64encode(buf.getvalue()).decode()
                img_flat_scaled = img.reshape((1, np.product(img.shape))) / 256.0
                [vec] = ndarray_to_dense_float_vectors(img_flat_scaled)
                yield dict(vec=vec.to_dict(), b64=b64)

    elif name == "word2vec-google":
        ds = gensimdl.load('glove-wiki-gigaword-50')
        for (word, info) in ds.vocab.items():
            [vec] = ndarray_to_dense_float_vectors(np.expand_dims(ds.vectors[info.index], 0))
            yield dict(word=word, vec=vec.to_dict())

    else:
        raise NameError(name)


if __name__ == "__main__":
    assert len(sys.argv) == 3, "Usage <script> <webapp url> <elasticsearch url> <datasets directory>"
    app_url = sys.argv[1]
    es_url = sys.argv[2]

    res = get(f"{app_url}/datasets")
    res.raise_for_status()
    datasets: List[Dataset] = Dataset.schema().load(res.json(), many=True)

    es = Elasticsearch([es_url])
    data_nodes = [n for n in es.nodes.info()['nodes'].values() if 'data' in n['roles']]
    index_body = dict(settings=dict(index=dict(number_of_shards=len(data_nodes))))

    for ds in datasets:

        for ex in ds.examples:
            print(f"Building index {ex.index}")

            if es.indices.exists(ex.index):
                es.indices.delete(ex.index)
            es.indices.create(ex.index, body=index_body)
            es.indices.refresh(ex.index)
            es.indices.put_mapping(json.loads(ex.mapping), index=ex.index)

            def gen():
                for i, _source in enumerate(load_docs(ds.source_name)):
                    print(_source)
                    yield {"_op_type": "index", "_index": ex.index, "_id": str(i + 1), "_source": _source}

            (n, errs) = bulk(es, gen())
            assert len(errs) == 0, errs
            print(f"Indexed {n} documents")

