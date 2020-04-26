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
from typing import List

from PIL import Image
from tqdm import tqdm


def annb(url: str, datadir: str):
    pass


def amazon_reviews(url: str, datadir: str):
    pass


def amazon_raw(url: str, datadir: str):
    pass


def amazon_phash(url: str, datadir: str, n: int = sys.maxsize):

    if not os.path.exists(datadir):
        os.mkdir(datadir)

    metafile = f"{datadir}/metadata.json.gz"
    if not os.path.exists(metafile):
        print(f"Downloading {url} to {metafile}")
        wget.download(url, metafile)

    imgsdir = f"{datadir}/images"
    if not os.path.exists(imgsdir):
        os.mkdir(imgsdir)

    # Keep a set of missing URLs.
    missingfile = f"{imgsdir}/missing.txt"
    if not os.path.exists(missingfile):
        with open(missingfile, "w"):
            pass
    with open(missingfile) as fp:
        missing_urls = set(fp.read().split('\n'))
    missingfp = open(missingfile, "w")

    # Download images.
    with gzip.open(metafile) as gzfp:
        lines = list(tqdm(gzfp, desc=f"Reading {metafile}"))[:n]
        with tqdm(lines, desc="Downloading images") as pbar:
            for d in map(eval, lines):
                if "imUrl" in d and d["imUrl"] not in missing_urls:
                    asin, url = d['asin'], d['imUrl']
                    imgfile = f"{imgsdir}/{asin}.jpg"
                    pbar.set_description(f"Processing {url} to {imgfile}")
                    if not os.path.exists(imgfile):
                        try:
                            urllib.request.urlretrieve(url, imgfile)
                        except urllib.error.HTTPError as ex:
                            missing_urls.add(url)
                            missingfp.write(f"{url}\n")
                            missingfp.flush()
                            print(f"Caught exception {ex} for url {url}", file=sys.stderr)
                        pbar.update()

    vecfile = f"{datadir}/vectors.json"

    with open(vecfile, "w") as vfp:
        for imgfile in tqdm(os.listdir(imgsdir), desc="pHashing images"):
            if imgfile.endswith(".jpg"):
                img = Image.open(f"{imgsdir}/{imgfile}")
                ph = phash(img, 64)
                for vec in ndarray_to_sparse_bool_vectors(ph.hash.reshape((1, ph.hash.size))):
                    vfp.write(f"{json.dumps(vec.to_dict())}\n")


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