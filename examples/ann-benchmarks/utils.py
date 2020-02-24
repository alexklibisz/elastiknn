import os
from typing import List

import h5py
from dataclasses import dataclass, field
from dataclasses_json import dataclass_json, config
from elastiknn.elastiknn_pb2 import ElastiKnnVector
from elastiknn.utils import canonical_vectors_to_elastiknn
from google.protobuf.json_format import MessageToDict


@dataclass_json
@dataclass
class Query:
    vector: ElastiKnnVector = field(metadata=config(encoder=MessageToDict))
    similarities: List[float] = field(metadata=config(encoder=lambda xx: list(map(float, xx))))
    indices: List[int] = field(metadata=config(encoder=lambda xx: list(map(float, xx))))


@dataclass_json
@dataclass
class Dataset:
    corpus: List[ElastiKnnVector] = field(metadata=config(encoder=lambda vecs: list(map(MessageToDict, vecs))))
    queries: List[Query]


def open_dataset(path: str) -> Dataset:
    hf = h5py.File(path, "r")
    train = []
    for i in range(len(hf['train'])):
        train += canonical_vectors_to_elastiknn(hf['train'][i:i+1, :])
    test = canonical_vectors_to_elastiknn(hf['test'][:, :])
    queries = [
        Query(vector=t, similarities=list(d), indices=list(n))
        for (t, d, n) in zip(test, hf['distances'], hf['neighbors'])
    ]
    return Dataset(corpus=list(train), queries=queries)


ANNB_ROOT = os.path.expanduser("~/.ann-benchmarks")