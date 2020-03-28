from enum import Enum
from typing import List

from dataclasses import dataclass


class Similarity(Enum):
    Jaccard = 1
    Hamming = 2
    L1 = 3
    L2 = 4
    Angular = 5


class Vec:

    @dataclass(init=False)
    class Base:
        pass

    @dataclass
    class SparseBool(Base):
        true_indices: List[int]
        total_indices: int

    @dataclass
    class DenseFloat(Base):
        values: List[float]

    @dataclass
    class Indexed(Base):
        index: str
        id: str
        field: str


class Mapping:

    @dataclass(init=False)
    class Base:
        dims: int

    @dataclass
    class SparseBool(Base):
        dims: int

    @dataclass
    class SparseIndexed(Base):
        dims: int

    @dataclass
    class JaccardLsh(Base):
        dims: int
        bands: int
        rows: int

    @dataclass
    class DenseFloat(Base):
        dims: int
