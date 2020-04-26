import gzip
import json
import os
import urllib.request
import urllib.error
import wget
import sys

from elastiknn.api import Vec
from elastiknn.utils import ndarray_to_sparse_bool_vectors
from imagehash import phash
from math import sqrt
from typing import List

from PIL import Image
from tqdm import tqdm


def mkdirp(d):
    if not os.path.exists(d):
        os.mkdir(d)


def rmrf(f):
    if os.path.exists(f):
        os.remove(f)


def touch(f):
    with open(f, "w"):
        pass


def annb(url: str, datadir: str):
    pass



def amazon_raw(url: str, datadir: str):
    pass


def amazon_phash(url: str, datadir: str, dim: int = 4096, n: int = sys.maxsize):

    assert sqrt(dim) == int(sqrt(dim))
    hash_size = int(sqrt(dim))

    if not os.path.exists(datadir):
        os.mkdir(datadir)

    metafile = f"{datadir}/metadata.json.gz"
    if not os.path.exists(metafile):
        print(f"Downloading {url} to {metafile}")
        wget.download(url, metafile)

    imgsdir = f"{datadir}/images"
    mkdirp(imgsdir)

    vecsdir = f"{datadir}/vecs"
    mkdirp(vecsdir)

    with gzip.open(metafile) as gzfp:
        lines = list(tqdm(gzfp, desc=f"Reading {metafile}"))[:n]
        with tqdm(lines, desc="Processing images") as pbar:
            for d in map(eval, lines):
                if "imUrl" not in d:
                    continue
                asin, url = d['asin'], d['imUrl']
                pbar.set_description(f"Processing {url}")
                imgfile = f"{imgsdir}/{asin}.jpg"
                imgfile_missing = f"{imgsdir}/{asin}.missing"
                vecfile = f"{vecsdir}/{asin}.json"
                if not os.path.exists(imgfile) and not os.path.exists(imgfile_missing):
                    try:
                        urllib.request.urlretrieve(url, imgfile)
                    except urllib.error.HTTPError as ex:
                        print(f"Caught exception {ex} for url {url}", file=sys.stderr)
                        rmrf(imgfile)
                        touch(imgfile_missing)
                if os.path.exists(imgfile) and not os.path.exists(vecfile):
                    img = Image.open(imgfile)
                    ph = phash(img, hash_size)
                    for vec in ndarray_to_sparse_bool_vectors(ph.hash.reshape((1, ph.hash.size))):
                        with open(vecfile, "w") as fp:
                            json.dump(vec.to_dict(), fp)
                pbar.update()


def main(argv: List[str]) -> int:
    data_dir = os.path.expanduser("~/.elastiknn-data")
    amazon_phash(
        "http://snap.stanford.edu/data/amazon/productGraph/categoryFiles/meta_Home_and_Kitchen.json.gz",
        f"{data_dir}/amazonhomephash",
        10000
    )
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))