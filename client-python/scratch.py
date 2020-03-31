
from elastiknn.api import *


q = NearestNeighborsQuery.Exact("f", Vec.Indexed("", "", ""), similarity=Similarity.Jaccard).to_dict()
print(q)