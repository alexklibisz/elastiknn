import gzip
import json
import os
import urllib.request
import urllib.error
from io import BytesIO
from itertools import islice

import PIL
import boto3
import wget
import sys

from botocore.exceptions import ClientError
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


def amazon_phash(metadata_url: str, imgs_s3_bucket: str, imgs_s3_prefix: str, datadir: str, dim: int = 4096, n: int = sys.maxsize):

    hash_size = int(sqrt(dim))
    assert sqrt(dim) == hash_size

    if not os.path.exists(datadir):
        os.mkdir(datadir)

    metafile = f"{datadir}/metadata.json.gz"
    if not os.path.exists(metafile):
        print(f"Downloading {metadata_url} to {metafile}")
        wget.download(metadata_url, metafile)

    vecsfile = f"{datadir}/vecs.json"

    # Get the number of lines already in the file so they can be skipped.
    # Note this might lead to some duplicates.
    nvecs = 0
    with open(vecsfile) as fp:
        for _ in fp:
            nvecs += 1

    # Open again for appending.
    vecsfp = open(f"{datadir}/vecs.json", "a")

    s3 = boto3.client('s3')

    with gzip.open(metafile) as gzfp:
        print(f"Skipping {nvecs} existing vectors")
        lines = islice(gzfp, nvecs, n)
        with tqdm(lines, desc="Processing images", total=n if n < sys.maxsize else None) as pbar:
            for d in map(eval, lines):
                if "imUrl" not in d or not d["imUrl"].endswith("jpg"):
                    continue
                asin = d['asin']
                pbar.set_description(f"Processing {asin}")
                try:
                    obj = s3.get_object(Bucket=imgs_s3_bucket, Key=f"{imgs_s3_prefix}/{asin}.jpg")
                    bytes = BytesIO(obj['Body'].read())
                    img = Image.open(bytes)
                except (PIL.UnidentifiedImageError, ClientError) as ex:
                    print(f"Error for image {asin}: {ex}\n", file=sys.stderr)
                ph = phash(img, hash_size)
                for vec in ndarray_to_sparse_bool_vectors(ph.hash.reshape((1, ph.hash.size))):
                    vecsfp.write(asin + ' ' + json.dumps(vec.to_dict(), separators=(',', ':')) + '\n')
                pbar.update(1)


def main(argv: List[str]) -> int:
    assert len(argv) == 4, "Usage: <script.py> <dataset name> <s3 bucket> <s3 prefix>"
    [dataset_name, s3_bucket, s3_prefix] = argv
    if dataset_name == "AmazonHomePhash":
        amazon_phash(
            "http://snap.stanford.edu/data/amazon/productGraph/categoryFiles/meta_Home_and_Kitchen.json.gz",
            "elastiknn-benchmarks",
            "data/amazon-reviews/images",
            s3_bucket,
            s3_prefix,
            4096
        )
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))