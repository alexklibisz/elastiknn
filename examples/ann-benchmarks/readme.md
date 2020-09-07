# ann-benchmarks

Here are some useful commands for working with elastiknn in ann-benchmarks:

Build the elastiknn image:

```
docker build -t ann-benchmarks -f install/Dockerfile .
docker build -t ann-benchmarks-elastiknn -f install/Dockerfile.elastiknn .
```

Run elastiknn for a specific dataset and algorithm:

```
python run.py --dataset glove-100-angular --algorithm elastiknn-exact --runs 1 --count 100
```

Plot results for a specific dataset:

```
python plot.py --dataset glove-100-angular --recompute --count 100 --y-log -o out.png
```