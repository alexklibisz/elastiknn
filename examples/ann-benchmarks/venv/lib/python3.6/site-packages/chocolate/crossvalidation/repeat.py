from collections import defaultdict

import numpy

class Repeat(object):
    """Repeats each experiment a given number of times and reduces the losses for
    the algorithms.

    The repetition cross-validation wraps the connection to handle repetition of
    experiments in the database. It is transparent to algorithms as it reduces
    the loss of repeated parameters and returns a list of results containing a single
    instance of each parameter set when :meth:`all_results` is called. If not all
    repetitions values are entered in the database before the next point is generated
    by the algorithm, the algorithm will see the reduced loss of the parameters
    that are completely evaluated only. Alternatively, if no repetition has finished
    its evaluation, the algorithm will see a :data:`None` as loss. :class:`Repeat` also
    handles assigning a repetition number to the tokens since the ``_chocolate_id`` will be
    repeated. Other token values, such as :class:`~chocolate.ThompsonSampling`'s
    ``_arm_id``, are also preserved.

    Args:
        repetitions: The number of repetitions to do for each experiment.
        reduce: The function to reduce the valid losses, usually average or median.
        rep_col: The database column name for the repetition number, it has to be unique.
    """
    def __init__(self, repetitions, reduce=numpy.mean, rep_col="_repetition_id"):
        self.repetitions = repetitions
        self.reduce = reduce
        self.rep_col = rep_col
        self.space = None

    def wrap_connection(self, connection):
        """
        """
        self.conn = connection
        self.orig_all_results = connection.all_results
        connection.all_results = self.all_results
        connection.count_results = self.count_results

    def all_results(self):
        results = self.orig_all_results()
        reduced_results = list()
        if results:
            loss_columns = [col for col in results[0].keys() if "_loss" in col]
        for result_group in self.group_repetitions(results):
            losses = {}
            for col in loss_columns:
                losses[col] = [r[col] for r in result_group if r[col] is not None]
            if any(len(l) > 0 for l in losses.values()):
                result = result_group[0].copy()
                for col in loss_columns:
                    result[col] = self.reduce(losses[col])
                reduced_results.append(result)
            else:
                reduced_results.append(result_group[0])
        return reduced_results

    def count_results(self):
        return len(self.all_results())

    def next(self):
        """Has to be called inside a lock

        Returns:

        """
        if self.repetitions > 1:
            if self.space is None:
                self.space = self.conn.get_space()

            results = self.orig_all_results()
            names = set(self.space.names())
            for result_group in self.group_repetitions(results):
                if len(result_group) < self.repetitions:
                    vec = [result_group[0][k] if k in result_group[0] else None for k in self.space.names()]
                    token = {k: result_group[0][k] for k in result_group[0].keys() if (k not in names) and (not "_loss" in k)}
                    token.update({self.rep_col: len(result_group)})
                    entry = result_group[0].copy()
                    # Ensure we don't have a duplicated id in the database
                    entry = self.conn.pop_id(entry)
                    token = self.conn.pop_id(token)
                    entry.update(token)
                    self.conn.insert_result(entry)
                    return token, self.space(vec)

            return {self.rep_col: 0}, None

        return None, None

    def group_repetitions(self, results):
        grouped = defaultdict(list)
        names = set(self.space.names())
        names.add("_loss")
        names.add(self.rep_col)

        for row in results:
            row = self.conn.pop_id(row)
            id_ = tuple((k, row[k]) for k in sorted(row.keys()) if (k not in names) and (not "_loss" in k))
            grouped[id_].append(row)

        return grouped.values()
