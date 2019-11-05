# ElastiKnn

ElastiKnn is an Elasticsearch plugin for exact and approximate nearest neighbors search in dense vector spaces.

## Features

1. Exact nearest neighbors search. This should only be used for testing and for small datasets (< ~10K vectors).
2. Approximate nearest neighbors search based on Locality Sensitive Hashing (LSH) and Multiprobe LSH. This scales well for large datasets; see the Performance section below for details.
3. Support for five distance functions: L1, L2, Angular, Hamming, and Jaccard.
4. Support for two common nearest neighbors queries: 
	- k nearest neighbors - i.e. _"give me the k nearest neighbors to some query vector"_
	- fixed-radius nearest neighbors - i.e. _"give me all neighbors within some radius of a query vector"_
5. Horizontal scalability. Vectors are stored as regular Elasticsearch documents and queries are implemented using standard Elasticsearch constructs.

## Usage

## Performance

## Development

## References