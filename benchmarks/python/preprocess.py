import gzip
import json
import os
import sys
from io import BytesIO
from itertools import islice
from math import sqrt
from time import time
from typing import List

import PIL
import boto3
import wget
from PIL import Image
from botocore.exceptions import ClientError
from elastiknn.utils import ndarray_to_sparse_bool_vectors
from imagehash import phash


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



def amazon_raw(features_url: str, output_s3_bucket: str, output_s3_prefix: str, normalize: bool):
    pass


def amazon_phash(metadata_url: str, imgs_s3_bucket: str, imgs_s3_prefix: str, local_data_dir: str, output_s3_bucket: str, output_s3_prefix: str, n: int = sys.maxsize):

    s3 = boto3.client('s3')

    # Check if it exists first.
    output_key = f"{output_s3_prefix}/vecs.json"
    existing = s3.list_objects(Bucket=output_s3_bucket, Prefix=output_key)
    if len(existing['Contents']) > 0:
        print(f"Key {output_key} already exists - returning")
        return

    metafile = f"{local_data_dir}/metadata.json.gz"
    if not os.path.exists(metafile):
        print(f"Downloading {metadata_url} to {metafile}")
        wget.download(metadata_url, metafile)

    vecsfile = f"{local_data_dir}/vecs.json"
    vecsfp = open(vecsfile, "a")

    hash_size = 4096

    with gzip.open(metafile) as gzfp:
        lines = islice(gzfp, 0, n)
        t0 = time()
        for i, d in enumerate(map(eval, lines)):
            if "imUrl" not in d or not d["imUrl"].endswith("jpg"):
                continue
            asin = d['asin']
            try:
                obj = s3.get_object(Bucket=imgs_s3_bucket, Key=f"{imgs_s3_prefix}/{asin}.jpg")
                bytes = BytesIO(obj['Body'].read())
                img = Image.open(bytes)
            except (PIL.UnidentifiedImageError, ClientError) as ex:
                print(f"Error for image {asin}: {ex}\n", file=sys.stderr)
            ph = phash(img, hash_size)
            for vec in ndarray_to_sparse_bool_vectors(ph.hash.reshape((1, ph.hash.size))):
                vecsfp.write(asin + ' ' + json.dumps(vec.to_dict(), separators=(',', ':')) + '\n')
            print(f"Processed {i}: {asin} - {((i + 1) / ((time() - t0) / 60)):.1f} vecs / minute")

    print(f"Copying {vecsfile} to {imgs_s3_bucket}, {imgs_s3_prefix}")
    s3.upload_file(Filename=vecsfile, Bucket=output_s3_bucket, Key=output_key)


def main(argv: List[str]) -> int:
    assert len(argv) == 5, "Usage: <script.py> <dataset name> <local data dir> <s3 bucket> <s3 prefix>"
    [dataset_name, local_data_dir, s3_bucket, s3_prefix] = argv[1:]
    if dataset_name == "amazonhome":
        amazon_raw(
            "http://snap.stanford.edu/data/amazon/productGraph/image_features/categoryFiles/image_features_Home_and_Kitchen.b",
            s3_bucket,
            s3_prefix,
            False
        )
    elif dataset_name == "amazonhomephash":
        amazon_phash(
            "http://snap.stanford.edu/data/amazon/productGraph/categoryFiles/meta_Home_and_Kitchen.json.gz",
            "elastiknn-benchmarks",
            "data/amazon-reviews/images",
            local_data_dir,
            s3_bucket,
            s3_prefix
        )
    else:
        print(dataset_name)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))