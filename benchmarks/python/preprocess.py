import array
import gzip
import json
import os
import sys
import urllib.request
from io import BytesIO
from itertools import islice
import numpy as np
from math import *
from time import time
from typing import List

import PIL
import boto3
from PIL import Image
from botocore.exceptions import ClientError
from elastiknn.api import Vec
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

def amazon_raw(features_s3_bucket: str, features_s3_key: str, local_data_dir: str, output_s3_bucket: str,
               output_s3_prefix: str, normalize: bool):

    s3 = boto3.client('s3')

    # Check if it exists first.
    output_key = f"{output_s3_prefix}/vecs.json"
    existing = s3.list_objects(Bucket=output_s3_bucket, Prefix=output_key)
    if 'Contents' in existing:
        print(f"Key {output_key} already exists - returning")
        return

    features_file = f"{local_data_dir}/vecs.b"
    if not os.path.exists(features_file):
        print(f"Downloading s3://{features_s3_bucket}/{features_s3_key} to {features_file}")
        s3.download_file(Bucket=features_s3_bucket, Key=features_s3_key, Filename=features_file)

    features_fp = open(features_file, 'rb')
    vecs_file = f"{local_data_dir}/vecs.json"
    vecs_fp = open(vecs_file, "w")

    i = 0
    t0 = time()
    while True:
        asin = features_fp.read(10).decode()
        if len(asin) == 0:
            break
        arr = array.array('f')
        arr.fromfile(features_fp, 4096)
        vec = Vec.DenseFloat(arr.tolist())
        if normalize:
            norm = sqrt(sum(map(lambda n: n * n, vec.values)))
            unit_values = [v / norm for v in vec.values]
            vec = Vec.DenseFloat(values=unit_values)
            norm_check = round(sqrt(sum(map(lambda n: n * n, vec.values))), 2)
            assert norm_check == 1.0, (vec, norm_check)
        vecs_fp.write(asin + ' ' + json.dumps(vec.to_dict(), separators=(',', ':')) + '\n')
        print(f"Processed {i}: {asin} - {((i + 1) / ((time() - t0) / 60)):.1f} vecs / minute")
        i += 1

    print(f"Copying {vecs_file} to s3://{output_s3_bucket}/{output_key}")
    s3.upload_file(Filename=vecs_file, Bucket=output_s3_bucket, Key=output_key)


def amazon_phash(metadata_s3_bucket: str, metadata_s3_key: str, imgs_s3_bucket: str, imgs_s3_prefix: str,
                 local_data_dir: str, output_s3_bucket: str, output_s3_prefix: str, n: int = sys.maxsize):

    s3 = boto3.client('s3')

    # Check if it exists first.
    output_key = f"{output_s3_prefix}/vecs.json"
    existing = s3.list_objects(Bucket=output_s3_bucket, Prefix=output_key)
    if 'Contents' in existing:
        print(f"Key {output_key} already exists - returning")
        return

    metadata_file = f"{local_data_dir}/metadata.json.gz"
    if not os.path.exists(metadata_file):
        print(f"Downloading s3://{metadata_s3_bucket}/{metadata_s3_key} to {metadata_file}")
        s3.download_file(Bucket=metadata_s3_bucket, Key=metadata_s3_key, Filename=metadata_file)

    vecs_file = f"{local_data_dir}/vecs.json"
    vecs_fp = open(vecs_file, "a")

    hash_size = 4096

    print(f"Writing vectors to {vecs_file}")

    with gzip.open(metadata_file) as gzfp:
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
                vecs_fp.write(asin + ' ' + json.dumps(vec.to_dict(), separators=(',', ':')) + '\n')
            print(f"Processed {i}: {asin} - {((i + 1) / ((time() - t0) / 60)):.1f} vecs / minute")

    print(f"Copying {vecs_file} to s3://{output_s3_bucket}/{output_key}")
    s3.upload_file(Filename=vecs_file, Bucket=output_s3_bucket, Key=output_key)


def main(argv: List[str]) -> int:
    assert len(argv) == 5, "Usage: <script.py> <dataset name> <local data dir> <s3 bucket> <s3 prefix>"
    [dataset_name, local_data_dir, s3_bucket, s3_prefix] = argv[1:]
    if dataset_name == "amazonhome":
        amazon_raw(
            "elastiknn-benchmarks",
            "data/raw/amazon-reviews/image_features_Home_and_Kitchen.b",
            local_data_dir,
            s3_bucket,
            s3_prefix,
            False
        )
    elif dataset_name == "amazonhomeunit":
        amazon_raw(
            "elastiknn-benchmarks",
            "data/raw/amazon-reviews/image_features_Home_and_Kitchen.b",
            local_data_dir,
            s3_bucket,
            s3_prefix,
            True
        )
    elif dataset_name == "amazonhomephash":
        amazon_phash(
            "elastiknn-benchmarks",
            "data/raw/amazon-reviews/meta_Home_and_Kitchen.json.gz",
            "elastiknn-benchmarks",
            "data/raw/amazon-reviews/images",
            local_data_dir,
            s3_bucket,
            s3_prefix
        )
    else:
        print(dataset_name)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))