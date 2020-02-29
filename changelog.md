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
