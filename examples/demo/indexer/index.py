import json
import os
import sys
import urllib.request
from base64 import b64encode
from io import BytesIO
from pprint import pprint
from time import time
from typing import Any
from zipfile import ZipFile

from PIL import Image
from dataclasses import dataclass
from dataclasses_json import dataclass_json, LetterCase
from elasticsearch import Elasticsearch
from elasticsearch.helpers import bulk
from elastiknn.utils import *
from more_itertools import chunked
from requests import get

DATA_DIR = os.path.expanduser("~/.elastiknn-data")

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
    source_link: str
    permalink: str
    examples: List[Example]


def generate_docs(name: str):
    if not os.path.exists(DATA_DIR):
        os.mkdir(DATA_DIR)

    if name == "word2vec-google-300":
        zipfile = f"{DATA_DIR}/nlpl-20-1.zip"
        if not os.path.exists(zipfile):
            urllib.request.urlretrieve("http://vectors.nlpl.eu/repository/20/1.zip", zipfile)
        with ZipFile(zipfile, "r") as arch:
            with arch.open("model.txt") as fp:
                for i, line in enumerate(fp):
                    tokens = line.decode().strip().split(' ')
                    word = tokens[0]
                    vec = Vec.DenseFloat(list(map(float, tokens[1:])))
                    if len(vec) == 300:
                        yield dict(word=word, vec=vec.to_dict())

    elif name in {"mnist", "mnist_binary"}:
        from keras.datasets import mnist
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
        from keras.datasets import cifar100, cifar10
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

    else:
        raise NameError(name)


if __name__ == "__main__":
    assert len(sys.argv) == 3, "Usage <script> <webapp url> <elasticsearch url> <datasets directory>"
    app_url = sys.argv[1]
    es_url = sys.argv[2]

    res = get(f"{app_url}/datasets")
    res.raise_for_status()
    pprint(res.json())
    datasets: List[Dataset] = Dataset.schema().load(res.json(), many=True)

    es = Elasticsearch([es_url])
    index_body = dict(settings=dict(index=dict(number_of_shards=os.cpu_count(), number_of_replicas=0)))

    for ds in datasets:

        todo = list(filter(lambda ex: not es.indices.exists(ex.index), ds.examples))
        if len(todo) == 0:
            continue

        for ex in todo:
            if es.indices.exists(ex.index):
                es.indices.delete(ex.index)
            es.indices.create(ex.index, body=index_body)
            es.indices.refresh(ex.index)
            es.indices.put_mapping(json.loads(ex.mapping), index=ex.index)

        n_docs, t0 = 0, time()

        for chunk in chunked(enumerate(generate_docs(ds.source_name)), 100):
            n_docs += len(chunk)
            for ex in todo:
                docs = [
                    {"_op_type": "index", "_index": ex.index, "_id": str(i + 1), "_source": _source}
                    for i, _source in chunk
                ]
                (n, errs) = bulk(es, docs)
                assert len(errs) == 0, errs

        print(f"Indexed {n_docs} documents in {int(time() - t0)} seconds")
