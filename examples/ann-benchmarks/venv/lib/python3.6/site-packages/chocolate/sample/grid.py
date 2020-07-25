import numpy

from ..base import SearchAlgorithm
from ..space import Distribution


class ParameterGrid(object):
    def __init__(self, space):
        self.space = space
        assert self.space.isdiscrete(), "Cannot place a grid on continuous distributions, use random sampling instead"

    def __len__(self):
        length = 0
        for subspace in self.space.subspaces():
            if not subspace:
                continue
            sizes = [len(v_list) if isinstance(v_list, Distribution) else 1 for v_list in subspace]
            length += numpy.product(sizes)

        return length

    def __getitem__(self, i):
        # This is a reimplementation of scikit-learn ParameterGrid __getitem__
        l = len(self)
        if -l <= i < 0:
            i += l
        elif i < -l or i >= l:
            raise IndexError("list index out of range")

        for subspace in self.space.subspaces():
            # XXX: could memoize information used here
            sizes = [len(v_list) if isinstance(v_list, Distribution) else 1 for v_list in subspace]
            total = numpy.product(sizes)

            if i >= total:
                # Try the next grid
                i -= total
            else:
                out = list()
                for v_list, n in zip(subspace, sizes):
                    i, offset = divmod(i, n)
                    if isinstance(v_list, Distribution):
                        out.append(v_list[offset])
                    else:
                        out.append(v_list)

                return out


class Grid(SearchAlgorithm):
    """Regular cartesian grid sampler.

    Samples the search space at every point of the grid formed by all
    dimensions. It requires every dimension to be a discrete distribution.

    Args:
        connection: A database connection object.
        space: The search space to explore with only discrete dimensions.
        crossvalidation: A cross-validation object that handles experiment
            repetition.
        clear_db: If set to :data:`True` and a conflict arise between the
            provided space and the space in the database, completely clear the
            database and set the space to the provided one.
    """
    def __init__(self, connection, space, crossvalidation=None, clear_db=False):
        super(Grid, self).__init__(connection, space, crossvalidation, clear_db)
        self.grid = ParameterGrid(self.space)

    def _next(self, token=None):
        """Sample the next point on the grid and add it to the database
        with loss set to :data:`None`.

        Returns:
            A tuple containing a unique token and a vector of length equal to
            the number of parameters.

        Raises:
            StopIteration: When the grid is exhausted.
        """
        i = self.conn.count_results()
        if i < len(self.grid):
            token = token or {}
            token.update({"_chocolate_id": i})
            # Sample next point in [0, 1)^n
            out = self.grid[i]

            # Signify next point to others using loss set to None
            # Transform to dict with parameter name
            entry = {k : v for k, v in zip(self.space.names(), out)}
            entry.update(token)
            self.conn.insert_result(entry)

            # return the true parameter set
            return token, self.space(out)

        raise StopIteration()
