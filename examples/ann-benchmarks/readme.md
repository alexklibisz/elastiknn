# ann-benchmarks

The actual ann-benchmarks implementation for Elastiknn is part of the [ann-benchmarks repo](https://github.com/erikbern/ann-benchmarks). 

This document just contains some tips and useful commands for working with elastiknn in ann-benchmarks:

Build the elastiknn image:

```
docker build -t ann-benchmarks -f install/Dockerfile .
docker build -t ann-benchmarks-elastiknn -f install/Dockerfile.elastiknn .
```

Run elastiknn for a specific dataset and algorithm:

```
python run.py --dataset glove-100-angular --algorithm elastiknn-exact --runs 1 --count 100 --force
```

Plot results for a specific dataset:

```
python plot.py --dataset glove-100-angular --recompute --count 100 --y-log -o out.png
```

When debugging, it's useful to restrict the size of `X_train` and `X_test` in the `run` method in `runner.py`, e.g.

```
X_train = numpy.array(D['train'])
X_test = numpy.array(D['test'][:500])
```