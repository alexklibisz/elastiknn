package com.klibisz.elastiknn.utils

trait Utils
    extends CirceUtils
    with ElastiKnnVectorUtils
    with KNearestNeighborsQueryUtils
    with ModelOptionsUtils
    with PerformanceUtils
    with GeneratedMessageUtils
    with SparseBoolVectorUtils
    with TraversableUtils
    with TryUtils

object Utils extends Utils
