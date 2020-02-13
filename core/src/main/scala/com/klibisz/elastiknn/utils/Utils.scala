package com.klibisz.elastiknn.utils

trait Utils
    extends CirceUtils
    with ElastiKnnVectorUtils
    with KNearestNeighborsQueryUtils
    with ModelOptionsUtils
    with PerformanceUtils
    with ProtobufUtils
    with SparseBoolVectorUtils
    with TraversableUtils
    with TryUtils

object Utils extends Utils
