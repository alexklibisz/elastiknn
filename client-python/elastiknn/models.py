from sklearn.base import BaseEstimator

class ElastiKnnModel(BaseEstimator):

    def __init__(self, url: str = 'http://localhost:9200', n_neighbors: int = None, algorithm: str = 'lsh', metric: str = 'jaccard'):



        pass
