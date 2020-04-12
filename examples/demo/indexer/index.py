import json
import os
import sys
import urllib.request
from base64 import b64encode
from io import BytesIO
from typing import Dict

import h5py
from PIL import Image
from dataclasses import dataclass
from dataclasses_json import dataclass_json, LetterCase
from elasticsearch import Elasticsearch
from elasticsearch.helpers import bulk
from elastiknn.utils import *
from requests import get

DATASETS_DIR = os.path.expanduser("~/.elastiknn-data")

@dataclass_json
@dataclass
class Example:
    name: str
    index: str
    mapping: str
    query: str


@dataclass_json(letter_case=LetterCase.CAMEL)
@dataclass
class Dataset:
    name: str
    pretty_name: str
    examples: List[Example]


def load_docs(name: str) -> List[Dict]:

    if not os.path.exists(DATASETS_DIR):
        os.mkdir(DATASETS_DIR)

    if name in {"mnist", "mnist_binary"}:
        path = f"{DATASETS_DIR}/mnist.hdf5"
        if not os.path.exists(path):
            urllib.request.urlretrieve("http://ann-benchmarks.com/mnist-784-euclidean.hdf5", path)
        with h5py.File(path, "r") as hf:
            mat = hf['train'][...]
        if name == "mnist_binary":
            vecs = canonical_vectors_to_elastiknn(mat / 256 > 0)
        else:
            vecs = canonical_vectors_to_elastiknn(mat)
        # Convert the vec into a base64 grayscale image.
        docs = []
        for imgvec, ekvec in zip(mat, vecs):
            img = Image.fromarray(imgvec.reshape(28, 28)).convert("L")
            buf = BytesIO()
            img.save(buf, format="JPEG")
            b64 = b64encode(buf.getvalue()).decode()
            docs.append(dict(vec=ekvec.to_dict(), b64=b64))
        return docs

    raise NameError


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
        docs = load_docs(ds.name)

        for ex in ds.examples:
            print(f"Building index {ex.index}")

            if es.indices.exists(ex.index):
                es.indices.delete(ex.index)
            es.indices.create(ex.index, body=index_body)
            es.indices.refresh(ex.index)
            es.indices.put_mapping(json.loads(ex.mapping), index=ex.index)

            def gen():
                for _source in docs:
                    yield {"_op_type": "index", "_index": ex.index, "_source": _source }

            (n, errs) = bulk(es, gen())
            assert len(errs) == 0, errs
            print(f"Indexed {n} documents")

