import array
import gzip
import json
import os
import sys
from io import BytesIO
from itertools import islice
from math import *
from random import Random
from time import time
from typing import List

import PIL
import boto3
import h5py
from PIL import Image
from botocore.exceptions import ClientError
from elastiknn.api import Vec
from elastiknn.utils import ndarray_to_sparse_bool_vectors
from imagehash import phash
from sklearn.neighbors import NearestNeighbors
from tqdm import tqdm


def exists(s3, bucket: str, key: str) -> bool:
    ex = 'Contents' in s3.list_objects(Bucket=bucket, Prefix=key)
    if ex:
        print(f"Key {key} already exists in bucket {bucket}")
        return True
    return False


def rounded_dense_float(values: List[float], n: int = 7) -> Vec.DenseFloat:
    def f(v):
        return round(float(v), n - int(floor(log10(abs(v)))) if abs(v) >= 1 else n)
    return Vec.DenseFloat(values = [f(v) for v in values])


def write_vec(fp, vec: Vec.Base):
    s = json.dumps(vec.to_dict(), separators=(',', ':')) + '\n'
    fp.write(s)


def annb(hdf5_s3_bucket: str, hdf5_s3_key: str, local_data_dir: str, output_s3_bucket: str, output_s3_prefix: str,
         scale_by_max: bool = False):

    s3 = boto3.client('s3')
    train_key = f"{output_s3_prefix}/train.json.gz"
    test_key = f"{output_s3_prefix}/test.json.gz"
    dist_key = f"{output_s3_prefix}/dist.json.gz"

    if exists(s3, output_s3_bucket, train_key) \
            and exists(s3, output_s3_bucket, test_key) \
            and exists(s3, output_s3_bucket, dist_key):
        return

    hdf5_file = f"{local_data_dir}/vecs.hdf5"

    if not os.path.exists(hdf5_file):
        print(f"Downloading s3://{hdf5_s3_bucket}/{hdf5_s3_key} to {hdf5_file}")
        s3.download_file(Bucket=hdf5_s3_bucket, Key=hdf5_s3_key, Filename=hdf5_file)

    train_file = f"{local_data_dir}/train.json.gz"
    test_file = f"{local_data_dir}/test.json.gz"
    dist_file = f"{local_data_dir}/distances.json.gz"

    hdf5_fp = h5py.File(hdf5_file, 'r')
    is_sparse = hdf5_fp['train'].dtype == bool

    train = hdf5_fp['train'][...]
    test = hdf5_fp['test'][...]

    if scale_by_max:
        max_scaler = train.max()
        train /= max_scaler
        test /= max_scaler

    knn = NearestNeighbors(n_neighbors=100, algorithm='brute', metric=hdf5_fp.attrs['distance'])
    knn.fit(train)
    (distances, _) = knn.kneighbors(test, return_distance=True)
    distances = 1 / (1 + distances)

    def write(iter_arr, fp):
        for arr in iter_arr:
            if is_sparse:
                vec = Vec.SparseBool([x for x, b in enumerate(arr) if b], len(arr))
            else:
                vec = rounded_dense_float(list(arr))
            write_vec(fp, vec)

    with gzip.open(train_file, "wt") as gzfp:
        write(tqdm(train, desc="train"), gzfp)

    with gzip.open(test_file, "wt") as gzfp:
        write(tqdm(test, desc="test"), gzfp)

    with gzip.open(dist_file, "wt") as gzfp:
        for arr in tqdm(distances, desc="distances"):
            lst = list(map(float, arr))
            gzfp.write(json.dumps(lst) + '\n')

    for (loc, key) in [(train_file, train_key), (test_file, test_key), (dist_file, dist_key)]:
        print(f"Copying {loc} to s3://{output_s3_bucket}/{key}")
        s3.upload_file(loc, output_s3_bucket, key)


def amazon_raw(features_s3_bucket: str, features_s3_key: str, local_data_dir: str, output_s3_bucket: str,
               output_s3_prefix: str, normalize: bool, total_size: int, test_size: int):

    s3 = boto3.client('s3')

    # Check if it exists first.
    train_key = f"{output_s3_prefix}/train.json.gz"
    test_key = f"{output_s3_prefix}/test.json.gz"
    if exists(s3, output_s3_bucket, train_key) and exists(s3, output_s3_bucket, test_key):
        return

    features_file = f"{local_data_dir}/vecs.b.gz"
    if not os.path.exists(features_file):
        print(f"Downloading s3://{features_s3_bucket}/{features_s3_key} to {features_file}")
        s3.download_file(Bucket=features_s3_bucket, Key=features_s3_key, Filename=features_file)

    features_fp = gzip.open(features_file, 'rb')

    train_file = f"{local_data_dir}/train.json.gz"
    test_file = f"{local_data_dir}/test.json.gz"

    # Setup to sample test vectors from the file.
    rng = Random(0)
    test_indexes = set(rng.sample(range(total_size), test_size))

    with gzip.open(train_file, "wt") as train_fp, gzip.open(test_file, "wt") as test_fp:
        i = 0
        t0 = time()
        while True:
            asin = features_fp.read(10).decode()
            if len(asin) == 0:
                break
            arr = array.array('f')
            arr.fromfile(features_fp, 4096)
            if normalize:
                norm = sqrt(sum(map(lambda n: n * n, arr.tolist())))
                unit_values = [v / norm for v in arr.tolist()]
                vec = rounded_dense_float(unit_values)
                norm_check = round(sqrt(sum(map(lambda n: n * n, vec.values))), 2)
                assert norm_check == 1.0, (vec, norm_check)
            else:
                vec = rounded_dense_float(arr.tolist())

            if i in test_indexes:
                write_vec(test_fp, asin, vec)
            else:
                write_vec(train_fp, asin, vec)

            print(f"Processed {i}: {asin} - {((i + 1) / ((time() - t0) / 60)):.1f} vecs / minute")
            i += 1

    print(f"Copying {train_file} to s3://{output_s3_bucket}/{train_key}")
    s3.upload_file(train_file, output_s3_bucket, train_key)
    print(f"Copying {test_file} to s3://{output_s3_bucket}/{test_key}")
    s3.upload_file(test_file, output_s3_bucket, test_key)


def amazon_phash(metadata_s3_bucket: str, metadata_s3_key: str, imgs_s3_bucket: str, imgs_s3_prefix: str,
                 local_data_dir: str, output_s3_bucket: str, output_s3_prefix: str, n: int = sys.maxsize):

    s3 = boto3.client('s3')

    # Check if it exists first.
    output_key = f"{output_s3_prefix}/vecs.json.gz"
    if exists(s3, output_s3_bucket, output_key):
        return

    metadata_file = f"{local_data_dir}/metadata.json.gz"
    if not os.path.exists(metadata_file):
        print(f"Downloading s3://{metadata_s3_bucket}/{metadata_s3_key} to {metadata_file}")
        s3.download_file(Bucket=metadata_s3_bucket, Key=metadata_s3_key, Filename=metadata_file)

    vecs_file = f"{local_data_dir}/vecs.json.gz"
    vecs_fp = gzip.open(vecs_file, "wt")

    hash_size = 64 # end up with a 4096-dimensional bit vector.

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
                write_vec(vecs_fp, asin, vec)
            print(f"Processed {i}: {asin} - {((i + 1) / ((time() - t0) / 60)):.1f} vecs / minute")
    vecs_fp.close() # Very important. Otherwise gzip file is invalid!

    print(f"Copying {vecs_file} to s3://{output_s3_bucket}/{output_key}")
    s3.upload_file(vecs_file, output_s3_bucket, output_key)


def main(argv: List[str]) -> int:
    assert len(argv) == 5, "Usage: <script.py> <dataset name> <local data dir> <s3 bucket> <s3 prefix>"
    [dataset_name, local_data_dir, s3_bucket, s3_prefix] = argv[1:]
    benchmarks_bucket = "elastiknn-benchmarks"
    if dataset_name == "amazonhome":
        amazon_raw(
            benchmarks_bucket,
            "data/raw/amazon-reviews/image_features_Home_and_Kitchen.b.gz",
            local_data_dir,
            s3_bucket,
            s3_prefix,
            False,
            436988,
            10000
        )
    elif dataset_name == "amazonhomeunit":
        amazon_raw(
            benchmarks_bucket,
            "data/raw/amazon-reviews/image_features_Home_and_Kitchen.b.gz",
            local_data_dir,
            s3_bucket,
            s3_prefix,
            True,
            436988,
            10000
        )
    elif dataset_name == "amazonhomephash":
        amazon_phash(
            benchmarks_bucket,
            "data/raw/amazon-reviews/meta_Home_and_Kitchen.json.gz",
            benchmarks_bucket,
            "data/raw/amazon-reviews/images",
            local_data_dir,
            s3_bucket,
            s3_prefix
        )
    elif dataset_name == "annbdeep1b":
        annb(
            benchmarks_bucket,
            "data/raw/annb/deep-image-96-angular.hdf5",
            local_data_dir,
            s3_bucket,
            s3_prefix
        )
    elif dataset_name == "annbfashionmnist":
        annb(
            benchmarks_bucket,
            "data/raw/annb/fashion-mnist-784-euclidean.hdf5",
            local_data_dir,
            s3_bucket,
            s3_prefix,
            scale_by_max=True
        )
    elif dataset_name == "annbgist":
        annb(
            benchmarks_bucket,
            "data/raw/annb/gist-960-euclidean.hdf5",
            local_data_dir,
            s3_bucket,
            s3_prefix
        )
    elif dataset_name == "annbglove25":
        annb(
            benchmarks_bucket,
            "data/raw/annb/glove-25-angular.hdf5",
            local_data_dir,
            s3_bucket,
            s3_prefix
        )
    elif dataset_name == "annbglove100":
        annb(
            benchmarks_bucket,
            "data/raw/annb/glove-100-angular.hdf5",
            local_data_dir,
            s3_bucket,
            s3_prefix
        )
    elif dataset_name == "annbkosarak":
        annb(
            benchmarks_bucket,
            "data/raw/annb/kosarak-jaccard.hdf5",
            local_data_dir,
            s3_bucket,
            s3_prefix
        )
    elif dataset_name == "annbmnist":
        annb(
            benchmarks_bucket,
            "data/raw/annb/mnist-784-euclidean.hdf5",
            local_data_dir,
            s3_bucket,
            s3_prefix,
            scale_by_max=True
        )
    elif dataset_name == "annbnyt":
        annb(
            benchmarks_bucket,
            "data/raw/annb/nytimes-256-angular.hdf5",
            local_data_dir,
            s3_bucket,
            s3_prefix
        )
    elif dataset_name == "annbsift":
        annb(
            benchmarks_bucket,
            "data/raw/annb/sift-128-euclidean.hdf5",
            local_data_dir,
            s3_bucket,
            s3_prefix,
            scale_by_max=True
        )
    else:
        raise RuntimeError(f"Unknown dataset: {dataset_name}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
