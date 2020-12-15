- Bumped Elasticsearch version to 7.10.0.
---
- No substantive changes. Just testing out new release setup.
---
- Bumped Elasticsearch version to 7.9.3.
---
- Fixed the function score query implementation. The first pass was kind of buggy for exact queries and totally wrong for approximate queries.
- Addressed a perplexing edge case that was causing an out-of-bounds exception in the MatchHashesAndScoreQuery. 
---
- Added support for [Function Score Queries](https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl-function-score-query.html)
  See the [Common Patterns](https://elastiknn.com/api/#common-patterns) section of the docs.
---
- Improved the Python ElastiknnModel's handling of empty query responses (i.e. no results). 
  Previously it threw an exception. Now it will just not populate the ID and distance arrays for that particular query.
---
- Upgraded to Elasticsearch version 7.9.2. No changes to the API. 
  It did require quite a bit of internal refactoring, mostly to the way vector types are implemented.
- Indices should be backwards compatible, however if you indexed on an earlier version, I'd recommend re-indexing and 
  setting the `index.elastiknn` setting to `true`.
---
- Adds an index-level setting: `index.elastiknn = true|false`, which defaults to `false`.
  Setting this to true tells Elastiknn to use a non-default storage format for doc values fields.
  Specifically, Elastiknn will use the latest Lucene formats for all fields except doc values, which will use the `Lucene70DocValuesFormat`.
  Using this specific doc values format is necessary to disable compression that makes Elastiknn extremely slow when upgraded past Elasticsearch version 7.6.x.
  Without this format, it's basically impossible to upgrade beyond 7.6.x.
  The root cause is a change that was made between Lucene 8.4.x and 8.5.x, which introduces more aggressive compression on binary doc values.
  This compression saves space, but becomes an extreme bottleneck for Elastiknn (40-100x slower queries), since Elastiknn stores vectors as binary doc values.
  Hopefully the Lucene folks will make this compression optional in the future.
  Read more here: https://issues.apache.org/jira/browse/LUCENE-9378
---
- Introduces a shorthand alternative format for dense and sparse vectors that makes it easier to work with ES-connectors that don't allow nested docs.
  - Dense vectors can be represented as a simple array: `{ "vec": [0.1, 0.2, 0.3, ...] }` is equivalent to `{ "vec": { "values": [0.1, 0.2, 0.3] }}`.
  - Sparse vectors can be represented as an array where the first element is the array of true indices, and the second is the number of total indices: `{"vec": [[1, 3, 5, ...], 100] }` is equivalent to `{ "vec": { "true_indices": [1,3,5,...], "total_indices": 100 }}`
- Added a logger warning when the approximate query matches fewer candidates than the specified number of candidates.
- Subtle modification to the DocIdSetIterator created by the MatchHashesAndScoreQuery to address issues 180 and 181.
  The gist of issue 180 is that the binary doc values iterator used to access vectors would attempt to visit the same
  document twice, and on the second visit the call to advanceExact would fail.
  The gist of the change is that the docID was previously initialized to be the smallest candidate docID.
  Initializing it to -1 seems to be the correct convention, and it makes that problem go away.
- Renamed all exceptions explicitly thrown by Elastiknn to ElastiknnFooException, e.g. ElastiknnIllegalArgumentException.
  This just makes it a bit more obvious where to look when debugging exceptions and errors. 
---
- No longer caching the mapping for the field being queried. Instead, using the internal mapper service to retrieve the mapping. 
---
- **Breaking internal change - you should re-index your vectors when moving to this version**
  - Storing the vector doc-values using just the field name. Previously used the field name with a suffix. Turns out this is not necessary, and it complicates "exists" queries.
---
- Tweaks to the Python client:
  - Removed threadpool from python `ElastiknnModel`. There's a non-trivial cost to using this, and the benchmarking code that uses this client is already single-threaded by design.
  - Added method `set_query_params` to python `ElastiknnModel`. This lets you update the query parameters once instead of doing it on every call to the `kneighbors` method. 
  - Using `filter_path` parameter in python `es.search()` method calls. This seems to add about 10 queries/sec. I guess there's some non-negligible cost to how the Elasticsearch python library parses JSON responses?
---
- Added optional `limit` parameter to all Lsh queries. For now I'm leaving it undocumented. I'm not 100% sure it's
a great idea. 
---
- Minor internal change to decrease the number of non-candidate doc IDs which are iterated over during a query.
- Changed documentation URL from elastiknn.klibisz.com to elastiknn.com.
---
- Fixed docs for running nearest neighbors query on a filtered subset of documents.
The original suggestion to use a bool query results in evaluating all docs. 
The correct way to do it is to use a standard query with a rescorer.  
---
- Fixed null pointer exception which was happening when running queries after deleting some vectors.
---
- More memory-efficient implementation of Python ElastiknnModel.fit method. Uses an iterator over the vectors instead of a list of the vectors.
---
- Renamed parameter `r` in L2Lsh mapping to `w`, which is more appropriate and common for "width".
- Updates and fixes in the Python client based on usage for ann-benchmarks. Mainly adding/fixing data classes in `elastiknn.api`.
---
- Updated `MatchHashesAndScoreQuery` so that approximate queries will emit no more than `candidates` doc IDs.
  This slightly decreases recall, since the previous implementation could emit > `candidates` IDs. 
---
- Support sparse bool query vectors with unsorted true indices.
---
- Added new submodules which can be used without Elasticsearch:
  - `com.klibisz.elastiknn:models` contains exact and approximate similarity models, all in Java with minimal dependencies.
  - `com.klibisz.elastiknn:lucene` contains the custom Lucene queries and some Lucene-related utilities used by Elastiknn.
  - `com.klibisz.elastiknn:api4s_2.12` contains Scala case classes and codecs representing the Elastiknn JSON API.
  - `com.klibisz.elastiknn:client-elastic4s_2.12` contains the Elastiknn Scala client. 
---
- Switched to less-naive implementation of multiprobe L2 LSH. Specifically, uses algorithm 1 from Qin, et. al. to generate
  perturbation sets lazily at query time instead of generating them exhaustively. This does not use the estimated 
  
  scoring optimization from that paper.
- Performance optimizations for approximate queries. Specifically using a faster sorting method to sort the hashes before
  retrieving matching docs from the shard.
---
- Introduced multiprobe L2 LSH. It's a small change. Search for `probes` in the API docs.
- Bug fix for an edge case in approximate queries.
---
- Changed to GPLv3 license. See: https://github.com/alexklibisz/elastiknn/blob/master/LICENSE.txt.
---
- Added Permutation Lsh model and query, based on paper _Large Scale Image Retrieval with Elasticsearch_ by Amato, et. al.
- Several internal improvements, including support for LSH models with repeated hashes.
---
- Performance improvements for LSH queries. 1.5-2x faster on regular benchmarks with randomized data. See PR #114.
---
- Fixed error with KNN queries against vectors that are stored in nested fields, e.g. `outer.inner.vec`.
---
- Switched LSH parameter names to more canonical equivalents: `bands -> L`, `rows -> k`,
  based on the [LSH wikipedia article](https://en.wikipedia.org/wiki/Locality-sensitive_hashing#LSH_algorithm_for_nearest_neighbor_search) 
  and material from Indyk, et. al, e.g. [these slides](http://people.csail.mit.edu/indyk/mmds.pdf).
- Added a `k` parameter to Hamming LSH model, which lets you concatenate > 1 bits to form a single hash value.
---
- Switched scala client to store the ID as a doc-value field. This avoids decompressing the document source
  when reading results, which is about 40% faster on benchmarks for both exact and approx. search.
---
- Re-implemented LSH and sparse-indexed queries using an optimized custom Lucene query based on the [TermInSetQuery](https://lucene.apache.org/core/8_5_0/core/org/apache/lucene/search/TermInSetQuery.html).
  This is 3-5x faster on LSH benchmarks.
- Updated L1, and L2 similarities such that they're bounded in [0,1].
---
- Added an option for LSH queries to use the more-like-this heuristics to pick a subset of LSH hashes to retrieve candidate vectors.
  Uses Lucene's [MoreLikeThis class](https://lucene.apache.org/core/8_5_0/queries/org/apache/lucene/queries/mlt/MoreLikeThis.html)
  to pick a subset of hashes based on index statistics. It's generally much faster than using _all_ of the hashes,
  yields comparable recall, but is still disabled by default. 
---
- Using ConstantScoreQuery to wrap the TermQuery's used for matching hashes in Elastiknn's SparseIndexQuery and LshQuery.
  Improves the SparseIndexedQuery benchmark from ~66 seconds to ~48 seconds.
  Improves the LshQuery benchmark from ~37 seconds to ~31 seconds. 
---
- Omitting norms in LSH and sparse indexed queries. 
  This shaves ~15% of runtime off of a sparse indexed benchmark. 
  Results for LSH weren't as meaningful unfortunately.   
---
- Removed the internal vector caching and instead using `sun.misc.Unsafe` to speed up vector serialization and deserialization.
  The result is actually faster queries _without_ caching than it previously had _with_ caching.
  Also able to remove the protobuf dependency which was previously used to serialize vectors.
- Upgraded Elasticsearch version from 7.4.0 to 7.6.2. 
  Attempted to use 7.7.1 but the Apache Lucene version used in 7.7.x introduces a performance regression ([Details](https://issues.apache.org/jira/browse/LUCENE-9378)).
  Switching from Java 13 to 14 also yields a slight speedup for intersections on sparse vectors.
---
- Internal change from custom Lucene queries to FunctionScoreQueries. This reduces quite a bit of boilerplate code and 
surface area for bugs and performance regressions.
- Add optional progress bar to Python ElastiknnModel.
---
- Updated client-elastic4s to use elastic4s version 7.6.0.
- Implemented a demo webapp using Play framework. Hosted at demo.elastiknn.klibisz.com.
---
- Implemented LSH for Hamming, Angular, and L2 similarities.
- First pass at a documentation website. 
---
- Introduced a cache for exact similarity queries that maintains deserialized vectors in memory instead of repeatedly
reading them and deserializing them. By default the cache entries expire after 90 seconds.
- Fixed a mapping issue that was causing warnings to be printed at runtime. Specifically, the term fields corresponding
to a vector should be given the same name as the field where the vector is stored. A bit confusing, but it works.
---
- Remove the usage of Protobufs at the API level. Instead implemented a more idiomatic Elasticsearch API. Now using c
ustom case classes in scala and data classes in Python, which is more tedious, but worth it for a more intuitive API. 
- Remove the pipelines in favor of processing/indexing vectors in the custom mapping. The model parameters are defined in 
the mapping and applied to any document field with type `elastiknn_sparse_bool_vector` or `elastiknn_dense_float_vector`.
This eliminates the need for a pipeline/processor and the need to maintain custom mappings for the indexed vectors.
- Implement all queries using custom Lucene queries. This is tightly coupled to the custom mappings, since the mappings
determine how vector hashes are stored and can be queried. For now I've been able to use very simple Lucene Term and
Boolean queries.
- Add a "sparse indexed" mapping for jaccard and hamming similarities. This stores the indices of sparse boolean vectors 
as Lucene terms, allowing you to run a term query to get the intersection of the query vector against all stored vectors.  
---
- Removed the `num_tables` argument from `JaccardLshOptions` as it's redundant to `num_bands`.
- Profiled and refactored the `JaccardLshModel` using the Ann-benchmarks Kosarak Jaccard dataset.
- Added an example program that grid-searches JaccardLshOptions for best performance and plots the Pareto front.
---
- LSH hashes are stored as a single `text` field with a `whitespace` analyzer `boolean` similarity instead of storing each
hash as a single keyword field. This resolves the problem of having too many fields in a document, which was causing
exceptions when using a large-ish number of LSH tables and min-hash bands (e.g. 20 and 40). The `whitespace` analyzer is
necessary to prevent `too_many_clauses` warnings. You can technically increase the permitted number of fields in a 
document, but using a single `text` field is, IMHO, a less invasive solution to the user. With this change I'm able to 
run LSH queries on the ann-benchmarks Kosarak dataset with 20 tables and 40 bands without any exceptions or warnings. 
- Added a REST endpoint at `PUT /_elastiknn/prepare_mapping` which takes an index and a `ProcessorOptions` object and
uses them to set up a correct mapping for that index. Calling this endpoint is implemented as `prepareMapping` in the 
scala client and `prepare_mapping` in the python client.  
---
- Got rid of base64 encoding/decoding in ElastiKnnVectorFieldMapper. This improves ann-benchmarks performance by about 20%.
---
- Improved exact Jaccard performance by implementing a critical path in Java so that it uses primitive `int []` arrays instead of boxed integers in scala.
---
- Fixed performance regression.
---
- Client and core library interface improvements.
- Added use_cache parameter to KNearestNeighborsQuery which signals that the vectors should only be read once from Lucene and then cached in memory.
---
- Releasing versioned python client library to PyPi.
---
- Releasing versioned elastiknn plugin zip file.
---
