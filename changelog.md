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
