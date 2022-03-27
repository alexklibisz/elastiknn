# ann-benchmarks

The actual ann-benchmarks implementation for Elastiknn is in the [ann-benchmarks repo](https://github.com/erikbern/ann-benchmarks).

This document just contains some tips and useful commands for working with elastiknn in ann-benchmarks:

**Build the elastiknn image**

```
docker build -t ann-benchmarks -f install/Dockerfile .
docker build -t ann-benchmarks-elastiknn -f install/Dockerfile.elastiknn .
```

**Run elastiknn for a specific dataset and algorithm**

Setting `--runs 5` actually matters a lot, since the JVM takes some time to "warm up".

```
python run.py --dataset fashion-mnist-784-euclidean --algorithm elastiknn-l2lsh --runs 5 --count 100
```

**Plot results for a specific dataset**

```
sudo chown -R $(whoami):$(whoami) results/
python plot.py --dataset glove-100-angular --recompute --count 100 --y-log -o out.png
```

**When debugging, restrict the size of `X_train` and `X_test` to save some time**

In the `run` method in `runner.py`:

```
X_train = numpy.array(D['train'])
X_test = numpy.array(D['test'][:500])
```

When debugging, use a local copy of the elastiknn client.

```
# Run in the ann-benchmarks project.
rsync -av --exclude={'venv','build','target','.minio','.git','.idea','.terraform'} ../elastiknn elastiknn
``` 

```
# Add these lines to the dockerfile.
COPY elastiknn /tmp/
RUN python3 -m pip install -e /tmp/elastiknn/client-python
```

Run a script that copies Elasticsearch logs into the local filesystem. Useful for inspecting logs of containers that crashed.

