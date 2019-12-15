#!/bin/bash
set -e
cd $(dirname $0)

python3 -m pip install virtualenv
python3 -m virtualenv venv
./venv/bin/pip install grpcio-tools
./venv/bin/python3 -m grpc_tools.protoc \
  --proto_path=../../core/src/main/proto \
  --proto_path=../../core/build/extracted-include-protos/main \
  --python_out=. \
  ../../core/src/main/proto/elastiknn/elastiknn.proto ../../core/build/extracted-include-protos/main/scalapb/scalapb.proto

