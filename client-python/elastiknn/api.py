from abc import ABC
from enum import Enum
from random import Random
from time import time
from typing import List

from dataclasses import dataclass

from elastiknn.codec import Codec


class Similarity(Enum):
    Jaccard = 1
    Hamming = 2
    L1 = 3
    L2 = 4
    Angular = 5


class Vec:

    @dataclass(init=False, frozen=True)
    class Base(Codec, ABC):
        pass

    @dataclass(frozen=True)
    class SparseBool(Base):
        true_indices: List[int]
        total_indices: int

        def to_dict(self):
            return self.__dict__

        def __len__(self):
            return self.total_indices

        @staticmethod
        def random(total_indices: int, rng: Random = Random(time())):
            true_indices = [i for i in range(total_indices) if rng.randint(0, 1)]
            return Vec.SparseBool(true_indices, total_indices)

    @dataclass(frozen=True)
    class DenseFloat(Base):
        values: List[float]

        def to_dict(self):
            return self.__dict__

        def __len__(self):
            return len(self.values)

    @dataclass(frozen=True)
    class Indexed(Base):
        index: str
        id: str
        field: str

        def to_dict(self):
            return self.__dict__


class Mapping:

    @dataclass(init=False, frozen=True)
    class Base(Codec, ABC):
        dims: int

    @dataclass(frozen=True)
    class SparseBool(Base):
        dims: int

        def to_dict(self):
            return {
                "type": "elastiknn_sparse_bool_vector",
                "elastiknn": {
                    "dims": self.dims
                }
            }

    @dataclass(frozen=True)
    class SparseIndexed(Base):
        dims: int

        def to_dict(self):
            return {
                "type": "elastiknn_sparse_bool_vector",
                "elastiknn": {
                    "dims": self.dims,
                    "model": "sparse_indexed"
                }
            }

    @dataclass(frozen=True)
    class JaccardLsh(Base):
        dims: int
        bands: int
        rows: int

        def to_dict(self):
            return {
                "type": "elastiknn_sparse_bool_vector",
                "elastiknn": {
                    "model": "lsh",
                    "similarity": "jaccard",
                    "dims": self.dims,
                    "bands": self.bands,
                    "rows": self.rows
                }
            }

    @dataclass(frozen=True)
    class HammingLsh(Base):
        dims: int
        bits: int

        def to_dict(self):
            return {
                "type": "elastiknn_sparse_bool_vector",
                "elastiknn": {
                    "model": "lsh",
                    "similarity": "jaccard",
                    "dims": self.dims,
                    "bits": self.bits
                }
            }

    @dataclass(frozen=True)
    class DenseFloat(Base):
        dims: int

        def to_dict(self):
            return {
                "type": "elastiknn_dense_float_vector",
                "elastiknn": {
                    "dims": self.dims
                }
            }


class NearestNeighborsQuery:

    @dataclass(frozen=True, init=False)
    class Base(Codec, ABC):
        field: str
        vec: Vec.Base
        similarity: Similarity

        def with_vec(self, vec: Vec.Base):
            raise NotImplementedError

    @dataclass(frozen=True)
    class Exact(Base):
        field: str
        vec: Vec.Base
        similarity: Similarity

        def to_dict(self):
            return {

                "field": self.field,
                "model": "exact",
                "similarity": self.similarity.name.lower(),
                "vec": self.vec.to_dict()
            }

        def with_vec(self, vec: Vec.Base):
            return NearestNeighborsQuery.Exact(self.field, vec, self.similarity)

    @dataclass(frozen=True)
    class SparseIndexed(Base):
        field: str
        vec: Vec.Base
        similarity: Similarity

        def to_dict(self):
            return {
                "field": self.field,
                "model": "sparse_indexed",
                "similarity": self.similarity.name.lower(),
                "vec": self.vec.to_dict()
            }

        def with_vec(self, vec: Vec.Base):
            return NearestNeighborsQuery.SparseIndexed(self.field, vec, self.similarity)

    @dataclass(frozen=True)
    class JaccardLsh(Base):
        field: str
        vec: Vec.Base
        similarity: Similarity = Similarity.Jaccard
        candidates: int = 1000

        def to_dict(self):
            return {
                "field": self.field,
                "model": "lsh",
                "similarity": self.similarity.name.lower(),
                "candidates": self.candidates,
                "vec": self.vec.to_dict()
            }

        def with_vec(self, vec: Vec.Base):
            return NearestNeighborsQuery.JaccardLsh(self.field, vec, self.similarity, self.candidates)

    @dataclass(frozen=True)
    class HammingLsh(Base):
        field: str
        vec: Vec.Base
        similarity: Similarity = Similarity.Hamming
        candidates: int = 1000

        def to_dict(self):
            return {
                "field": self.field,
                "model": "lsh",
                "similarity": self.similarity.name.lower(),
                "candidates": self.candidates,
                "vec": self.vec.to_dict()
            }

        def with_vec(self, vec: Vec.Base):
            return NearestNeighborsQuery.HammingLsh(self.field, vec, self.similarity, self.candidates)


