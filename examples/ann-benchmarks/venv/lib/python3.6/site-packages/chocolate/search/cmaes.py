from itertools import groupby
from operator import itemgetter

import numpy

from ..base import SearchAlgorithm
from ..mo import argsortNondominated, dominates, hypervolume_indicator


class CMAES(SearchAlgorithm):
    """Covariance Matrix Adaptation Evolution Strategy minimization method.

    A CMA-ES strategy that combines the :math:`(1 + \\lambda)` paradigm
    [Igel2007]_, the mixed integer modification [Hansen2011]_ and active
    covariance update [Arnold2010]_. It generates a single new point per
    iteration and adds a random step mutation to dimensions that undergoes a
    too small modification. Even if it includes the mixed integer
    modification, CMA-ES does not handle well dimensions without variance and
    thus it should be used with care on search spaces with conditional
    dimensions.

    Args:
        connection: A database connection object.
        space: The search space to explore.
        crossvalidation: A cross-validation object that handles experiment
            repetition.
        clear_db: If set to :data:`True` and a conflict arise between the
            provided space and the space in the database, completely clear the
            database and set the space to the provided one.
        **params: Additional parameters to pass to the strategy as described in
            the following table, along with default values.

            +----------------+---------------------------+----------------------------+
            | Parameter      | Default value             | Details                    |
            +================+===========================+============================+
            | ``d``          | ``1 + ndim / 2``          | Damping for step-size.     |
            +----------------+---------------------------+----------------------------+
            | ``ptarg``      | ``1 / 3``                 | Taget success rate.        |
            +----------------+---------------------------+----------------------------+
            | ``cp``         | ``ptarg / (2 + ptarg)``   | Step size learning rate.   |
            +----------------+---------------------------+----------------------------+
            | ``cc``         | ``2 / (ndim + 2)``        | Cumulation time horizon.   |
            +----------------+---------------------------+----------------------------+
            | ``ccovp``      | ``2 / (ndim**2 + 6)``     | Covariance matrix positive |
            |                |                           | learning rate.             |
            +----------------+---------------------------+----------------------------+
            | ``ccovn``      | ``0.4 / (ndim**1.6 + 1)`` | Covariance matrix negative |
            |                |                           | learning rate.             |
            +----------------+---------------------------+----------------------------+
            | ``pthresh``    | ``0.44``                  | Threshold success rate.    |
            +----------------+---------------------------+----------------------------+

    .. note::

       To reduce sampling, the constraint to the search space bounding box is enforced
       by repairing the individuals and adjusting the taken step. This will lead to a
       slight over sampling of the boundaries when local optimums are close to them.

    .. [Igel2007] Igel, Hansen, Roth. Covariance matrix adaptation for
       multi-objective optimization. 2007

    .. [Arnold2010] Arnold and Hansen. Active covariance matrix adaptation for
       the (1 + 1)-CMA-ES. 2010.

    .. [Hansen2011] Hansen. A CMA-ES for Mixed-Integer Nonlinear Optimization.
       Research Report] RR-7751, INRIA. 2011
    """

    def __init__(self, connection, space, crossvalidation=None, clear_db=False, **params):
        super(CMAES, self).__init__(connection, space, crossvalidation, clear_db)
        self.random_state = numpy.random.RandomState()
        self.params = params

    def _next(self, token=None):
        """Retrieve the next point to evaluate based on available data in the
        database. Each time :meth:`next` is called, the algorithm will reinitialize
        it-self based on the data in the database.

        Returns:
            A tuple containing a unique token and a fully qualified parameter set.
        """
        self._init()

        # Check what is available in that database
        results = {r["_chocolate_id"]: r for r in self.conn.all_results()}
        ancestors, ancestors_ids = self._load_ancestors(results)
        bootstrap = self._load_bootstrap(results, ancestors_ids)

        # Rank-mu update on individuals created from another algorithm
        self._bootstrap(bootstrap)
        # _bootstrap sets the parent if enough candidates are available (>= 4)

        # If the parent is still None and ancestors are available
        # set the parent to the first evaluated candidate if any
        if self.parent is None and len(ancestors) > 0:
            self.parent = next((a for a in ancestors if a["loss"] is not None), None)

        # Generate the next point
        token = token or {}
        token.update({"_chocolate_id": self.conn.count_results()})

        # If the parent is still None, no information available
        if self.parent is None:
            out = self.random_state.rand(self.dim)

            # Add the step to the complementary table
            # Transform to dict with parameter names
            entry = {str(k): v for k, v in zip(self.space.names(), out)}
            entry.update(_ancestor_id=-1, **token)
            self.conn.insert_complementary(entry)

        else:
            # Simulate the CMA-ES update for each ancestor.
            for key, group in groupby(ancestors[1:], key=itemgetter("ancestor_id")):
                # If the loss for this entry is not yet availabe, don't include it
                group = list(group)
                self.lambda_ = len(group)
                self._configure()  # Adjust constants that depends on lambda
                self._update_internals(group)

            # Generate a single candidate at a time
            self.lambda_ = 1
            self._configure()

            # The ancestor id is the last candidate that participated in the
            # covariance matrix update
            ancestor_id = next((a["chocolate_id"] for a in reversed(bootstrap + ancestors) if a["loss"] is not None), None)
            assert ancestor_id is not None, "Invalid ancestor id"

            out, y = self._generate()

            # Add the step to the complementary table
            # Transform to dict with parameter names
            entry = {str(k): v for k, v in zip(self.space.names(), y)}
            entry.update(_ancestor_id=ancestor_id, **token)
            self.conn.insert_complementary(entry)

        # Signify the first point to others using loss set to None
        # Transform to dict with parameter names
        entry = {str(k): v for k, v in zip(self.space.names(), out)}
        entry.update(token)
        self.conn.insert_result(entry)

        # return the true parameter set
        return token, self.space(out)

    def _init(self):
        self.parent = None
        self.sigma = 0.2
        self.dim = len(self.space)

        self.C = numpy.identity(self.dim)
        self.A = numpy.linalg.cholesky(self.C)

        self.pc = numpy.zeros(self.dim)

        # Covariance matrix adaptation
        self.cc = self.params.get("cc", 2.0 / (self.dim + 2.0))
        self.ccovp = self.params.get("ccovp", 2.0 / (self.dim ** 2 + 6.0))
        self.ccovn = self.params.get("ccovn", 0.4 / (self.dim ** 1.6 + 1.0))
        self.pthresh = self.params.get("pthresh", 0.44)

        # Active covariance update for unsucessful candidates
        self.ancestors = list()

        # Constraint vectors for covariance adaptation
        # We work in the unit box [0, 1)
        self.constraints = numpy.zeros((self.dim * 2, self.dim))

        self.S_int = numpy.zeros(self.dim)
        for i, s in enumerate(self.space.steps()):
            if s is not None:
                self.S_int[i] = s

        self.i_I_R = numpy.flatnonzero(2 * self.sigma * numpy.diag(self.C) ** 0.5 < self.S_int)
        self.update_count = 0

    def _configure(self):
        self.d = self.params.get("d", 1.0 + self.dim / (2.0 * self.lambda_))
        self.ptarg = self.params.get("ptarg", 1.0 / (5 + numpy.sqrt(self.lambda_) / 2.0))
        self.cp = self.params.get("cp", self.ptarg * self.lambda_ / (2 + self.ptarg * self.lambda_))

        # TODO: Check validity here, shouldn't self.psucc be initialized in _init?
        if self.update_count == 0:
            self.psucc = self.ptarg

    def _load_ancestors(self, results):
        # Get a list of the actual ancestor and the complementary information
        # on that ancestor
        ancestors = list()
        ancestors_ids = set()
        for c in sorted(self.conn.all_complementary(), key=itemgetter("_chocolate_id")):
            candidate = dict()
            candidate["step"] = numpy.array([c[str(k)] for k in self.space.names()])
            candidate["chocolate_id"] = c["_chocolate_id"]
            candidate["ancestor_id"] = c["_ancestor_id"]
            candidate["X"] = numpy.array([results[c["_chocolate_id"]][str(k)] for k in self.space.names()])
            candidate["loss"] = results[c["_chocolate_id"]].get("_loss", None)

            ancestors.append(candidate)
            ancestors_ids.add(candidate["chocolate_id"])

        return ancestors, ancestors_ids

    def _load_bootstrap(self, results, ancestors_ids):
        # Find individuals produced by another algorithm
        bootstrap = list()
        for _, c in sorted(results.items()):
            # Skip those included in ancestors
            if c["_chocolate_id"] in ancestors_ids:
                continue

            candidate = dict()
            # The initial distribution is assumed uniform and centred on 0.5^n
            candidate["step"] = numpy.array([c[str(k)] - 0.5 for k in self.space.names()])
            candidate["X"] = numpy.array([results[c["_chocolate_id"]][str(k)] for k in self.space.names()])
            candidate["chocolate_id"] = c["_chocolate_id"]
            candidate["ancestor_id"] = -1
            candidate["loss"] = c.get("_loss", None)

            bootstrap.append(candidate)

        return bootstrap

    def _bootstrap(self, candidates):
        # Remove not evaluated
        candidates = [c for c in candidates if c["loss"] is not None]

        # Rank-mu update for covariance matrix
        if len(candidates) >= 4:
            mu = int(len(candidates) / 2)
            # superlinear weights (the usual default)
            weights = numpy.log(mu + 0.5) - numpy.log(numpy.arange(1, mu + 1))
            weights /= sum(weights)
            c1 = 2 / len(candidates[0]) ** 2
            cmu = mu / len(candidates[0]) ** 2

            candidates.sort(key=itemgetter("loss"))
            c_array = numpy.array([c["step"] for c in candidates[:mu]])
            cw = numpy.sum(weights * c_array.T, axis=1)

            self.pc = (1 - self.cc) * self.pc + numpy.sqrt(1 - (1 - self.cc) ** 2) * numpy.sqrt(mu) * cw
            self.C = (1 - c1 - cmu) * self.C + c1 * numpy.outer(self.pc, self.pc) + cmu * numpy.dot(weights * c_array.T, c_array)

            self.parent = candidates[0]

    def _update_internals(self, candidates):
        assert self.parent is not None, "No parent for CMA-ES internal update"
        assert "loss" in self.parent, "Parent has no loss in CMA-ES internal update"
        assert self.parent["loss"] is not None, "Invalid loss for CMA-ES parent"
        # Remove not evaluated
        candidates = [c for c in candidates if c["loss"] is not None]

        if len(candidates) == 0:
            # Empty group, abort
            return

        # Is the new point better than the parent?
        candidates.sort(key=itemgetter("loss"))
        lambda_succ = sum(s["loss"] <= self.parent["loss"] for s in candidates)
        p_succ = float(lambda_succ) / self.lambda_
        self.psucc = (1 - self.cp) * self.psucc + self.cp * p_succ

        # On success update the matrices C, A == B*D and evolution path
        if candidates[0]["loss"] <= self.parent["loss"]:
            self.parent = candidates[0].copy()
            if self.psucc < self.pthresh:
                self.pc = (1 - self.cc) * self.pc + numpy.sqrt(self.cc * (2 - self.cc)) * candidates[0]["step"]
                self.C = (1 - self.ccovp) * self.C + self.ccovp * numpy.outer(self.pc, self.pc)
            else:
                self.pc = (1 - self.cc) * self.pc
                self.C = (1 - self.ccovp) * self.C + self.ccovp * (numpy.outer(self.pc, self.pc)
                                                                   + self.cc * (2 - self.cc) * self.C)

            self.A = numpy.linalg.cholesky(self.C)

        elif len(self.ancestors) >= 5 and candidates[0]["loss"] > sorted(s["loss"] for s in self.ancestors)[-1]:
            # Active negative covariance update
            z = numpy.dot(numpy.linalg.inv(self.A), candidates[0]["step"])
            n_z2 = numpy.linalg.norm(z) ** 2
            if 1 - self.ccovn * n_z2 / (1 + self.ccovn) < 0.5:
                ccovn = 1 / (2 * numpy.linalg.norm(z) ** 2 - 1)
            else:
                ccovn = self.ccovn
            self.A = numpy.sqrt(1 + ccovn) * self.A + numpy.sqrt(1 + ccovn) / n_z2 * (
                numpy.sqrt(1 - ccovn * n_z2 / (1 + ccovn)) - 1) * numpy.dot(self.A, numpy.outer(z, z))
            self.C = numpy.dot(self.A, self.A.T)  # Yup we still have an update o C

        # Keep a list of ancestors sorted by order of appearance
        self.ancestors.insert(0, candidates[0])
        if len(self.ancestors) > 5:
            self.ancestors.pop(-1)

        # Update the step size
        self.sigma = self.sigma * numpy.exp(1.0 / self.d * (self.psucc - self.ptarg) / (1 - self.ptarg))

        # Update the dimensions where integer mutation is needed
        self.i_I_R = numpy.flatnonzero(2 * self.sigma * numpy.diag(self.C) ** 0.5 < self.S_int)
        self.update_count += 1

    def _generate(self):
        n_I_R = self.i_I_R.shape[0]
        R_int = numpy.zeros(self.dim)

        # Mixed integer CMA-ES is developped for (mu/mu , lambda)
        # We have a (1 + 1) setting, thus we make the integer mutation probabilistic.
        # The integer mutation is lambda / 2 if all dimensions are integers or
        # min(lambda / 2 - 1, lambda / 10 + n_I_R + 1), minus 1 accounts for
        # the last new candidate getting its integer mutation from the last best
        # solution.
        if n_I_R == self.dim:
            p = 0.5
        else:
            p = min(0.5, 0.1 + n_I_R / self.dim)

        if n_I_R > 0 and self.random_state.rand() < p:
            Rp = numpy.zeros(self.dim)
            Rpp = numpy.zeros(self.dim)

            # Ri' has exactly one of its components set to one.
            # The Ri' are dependent in that the number of mutations for each coordinate
            # differs at most by one.
            j = self.random_state.choice(self.i_I_R)
            Rp[j] = 1
            Rpp[j] = self.random_state.geometric(p=0.7 ** (1.0 / n_I_R)) - 1

            I_pm1 = (-1) ** self.random_state.randint(0, 2, self.dim)
            R_int = I_pm1 * (Rp + Rpp)

        y = numpy.dot(self.random_state.standard_normal(self.dim), self.A.T)
        arz = self.parent["X"] + self.sigma * y + self.S_int * R_int

        # Repair candidates outside [0, 1)
        arz_corr = numpy.clip(arz, 0, 1 - 1e-8)
        y_corr = ((arz_corr - arz) / self.sigma) + y
        return arz_corr, y_corr


class MOCMAES(SearchAlgorithm):
    """Multi-Objective Covariance Matrix Adaptation Evolution Strategy.

    A CMA-ES strategy for multi-objective optimization. It combines the improved
    step size adaptation [Voss2010]_ and
    the mixed integer modification [Hansen2011]_. It generates a single new point per
    iteration and adds a random step mutation to dimensions that undergoes a
    too small modification. Even if it includes the mixed integer
    modification, MO-CMA-ES does not handle well dimensions without variance and
    thus it should be used with care on search spaces with conditional
    dimensions.

    Args:
        connection: A database connection object.
        space: The search space to explore.
        crossvalidation: A cross-validation object that handles experiment
            repetition.
        mu: The number of parents used to generate the candidates. The higher this
            number is the better the Parato front coverage will be, but the longer
            it will take to converge.
        clear_db: If set to :data:`True` and a conflict arise between the
            provided space and the space in the database, completely clear the
            database and set the space to the provided one.
        **params: Additional parameters to pass to the strategy as described in
            the following table, along with default values.

            +----------------+------------------------------+----------------------------+
            | Parameter      | Default value                | Details                    |
            +================+==============================+============================+
            | ``d``          | ``1 + ndim / 2``             | Damping for step-size.     |
            +----------------+------------------------------+----------------------------+
            | ``ptarg``      | ``1 / 3``                    | Taget success rate.        |
            +----------------+------------------------------+----------------------------+
            | ``cp``         | ``ptarg / (2 + ptarg)``      | Step size learning rate.   |
            +----------------+------------------------------+----------------------------+
            | ``cc``         | ``2 / (ndim + 2)``           | Cumulation time horizon.   |
            +----------------+------------------------------+----------------------------+
            | ``ccov``       | ``2 / (ndim**2 + 6)``        | Covariance matrix learning |
            |                |                              | rate.                      |
            +----------------+------------------------------+----------------------------+
            | ``pthresh``    | ``0.44``                     | Threshold success rate.    |
            +----------------+------------------------------+----------------------------+
            | ``indicator``  | ``mo.hypervolume_indicator`` | Indicator function used    |
            |                |                              | for ranking candidates     |
            +----------------+------------------------------+----------------------------+

    .. note::

       To reduce sampling, the constraint to the search space bounding box is enforced
       by repairing the individuals and adjusting the taken step. This will lead to a
       slight over sampling of the boundaries when local optimums are close to them.

    .. [Voss2010] Voss, Hansen, Igel. Improved Step Size Adaptation for the MO-CMA-ES.
       In proc. GECCO'10, 2010.

    .. [Hansen2011] Hansen. A CMA-ES for Mixed-Integer Nonlinear Optimization.
       [Research Report] RR-7751, INRIA. 2011

    """
    def __init__(self, connection, space, mu, crossvalidation=None, clear_db=False, **params):
        super(MOCMAES, self).__init__(connection, space, crossvalidation, clear_db)
        self.random_state = numpy.random.RandomState()
        self.mu = mu
        self.params = params

    def _next(self, token=None):
        """Retrieve the next point to evaluate based on available data in the
        database. Each time :meth:`next` is called, the algorithm will reinitialize
        it-self based on the data in the database.

        Returns:
            A tuple containing a unique token and a fully qualified parameter set.
        """
        self._init()

        # Check what is available in that database
        results = {r["_chocolate_id"]: r for r in self.conn.all_results()}
        ancestors, ancestors_ids = self._load_ancestors(results)
        bootstrap = self._load_bootstrap(results, ancestors_ids)

        # Select mu parents from the individuals created by another algorithm
        self.parents = [bootstrap[i] for i in self._select(bootstrap)]

        # Generate the next point
        token = token or {}
        token.update({"_chocolate_id": self.conn.count_results()})

        if len(self.parents) < self.mu:
            # There is not enough parents yet, generate some at random
            # We don't add those in the complementary db
            out = self.random_state.rand(self.dim)

        else:
            # Do CMA-ES update for each ancestor
            # Ancestors are already sorted by chocolate_id
            for c in ancestors:
                self._update_internals(c)

            out, y, p_idx = self._generate()

            # Add the step to the complementary table
            entry = {str(k): v for k, v in zip(self.space.names(), y)}
            entry.update(_parent_idx=p_idx, **token)
            self.conn.insert_complementary(entry)

        # Transform to dict with parameter names
        entry = {str(k): v for k, v in zip(self.space.names(), out)}
        entry.update(token)
        self.conn.insert_result(entry)

        # return the true parameter set
        return token, self.space(out)

    def _init(self):
        self.parents = list()
        self.dim = len(self.space)

        # Step size control
        self.d = self.params.get("d", 1.0 + self.dim / 2.0)
        self.ptarg = self.params.get("ptarg", 1.0 / (5.0 + 0.5))
        self.cp = self.params.get("cp", self.ptarg / (2.0 + self.ptarg))

        # Covariance matrix adaptation
        self.cc = self.params.get("cc", 2.0 / (self.dim + 2.0))
        self.ccov = self.params.get("ccov", 2.0 / (self.dim ** 2 + 6.0))
        self.pthresh = self.params.get("pthresh", 0.44)

        # Internal parameters associated to the mu parent
        self.sigmas = [0.2] * self.mu
        self.C = [numpy.identity(self.dim) for _ in range(self.mu)]
        self.A = [numpy.linalg.cholesky(self.C[i]) for i in range(self.mu)]
        self.pc = [numpy.zeros(self.dim) for _ in range(self.mu)]
        self.psucc = [self.ptarg] * self.mu

        self.indicator = self.params.get("indicator", hypervolume_indicator)

        # Constraint vectors for covariance adaptation
        # We work in the unit box [0, 1)
        self.constraints = [numpy.zeros((self.dim * 2, self.dim)) for _ in range(self.mu)]

        self.S_int = numpy.zeros(self.dim)
        for i, s in enumerate(self.space.steps()):
            if s is not None:
                self.S_int[i] = s

        # Integer mutation parameters
        im = numpy.flatnonzero(2 * self.sigmas[0] * numpy.diag(self.C[0]) ** 0.5 < self.S_int)
        self.i_I_R = [im.copy() for i in range(self.mu)]

    def _load_ancestors(self, results):
        # Get a list of the actual ancestor and the complementary information
        # on that ancestor
        ancestors = list()
        ancestors_ids = set()
        for c in sorted(self.conn.all_complementary(), key=itemgetter("_chocolate_id")):
            candidate = dict()
            candidate["step"] = numpy.array([c[str(k)] for k in self.space.names()])
            candidate["chocolate_id"] = c["_chocolate_id"]
            candidate["parent_idx"] = c["_parent_idx"]
            candidate["loss"] = None

            candidate["X"] = numpy.array([results[c["_chocolate_id"]][str(k)] for k in self.space.names()])
            losses = list(v for k, v in sorted(results[c["_chocolate_id"]].items()) if k.startswith("_loss"))
            if len(losses) > 0 and all(l is not None for l in losses):
                candidate["loss"] = losses

            ancestors.append(candidate)
            ancestors_ids.add(candidate["chocolate_id"])

        return ancestors, ancestors_ids

    def _load_bootstrap(self, results, ancestors_ids):
        # Find individuals produced by another algorithm
        bootstrap = list()
        for _, c in sorted(results.items()):
            # Skip those included in ancestors
            if c["_chocolate_id"] in ancestors_ids:
                continue

            candidate = dict()
            # The initial distribution is assumed uniform and centred on 0.5^n
            candidate["step"] = numpy.array([c[str(k)] - 0.5 for k in self.space.names()])
            candidate["X"] = numpy.array([results[c["_chocolate_id"]][str(k)] for k in self.space.names()])
            candidate["chocolate_id"] = c["_chocolate_id"]
            candidate["ancestor_id"] = -1
            candidate["loss"] = None

            losses = list(v for k, v in sorted(results[c["_chocolate_id"]].items()) if k.startswith("_loss"))
            if len(losses) > 0 and all(l is not None for l in losses):
                candidate["loss"] = losses

            bootstrap.append(candidate)

        return bootstrap

    def _select(self, candidates):
        """Returns the indices of the selected individuals"""
        valid_candidates = [(i, c) for i, c in enumerate(candidates) if c["loss"] is not None]
        if len(valid_candidates) <= self.mu:
            return [i for i, _ in valid_candidates]

        losses = [c["loss"] for _, c in valid_candidates]
        pareto_fronts = argsortNondominated(losses, len(losses))

        chosen = list()
        mid_front = None

        # Fill the next population (chosen) with the fronts until there is not enouch space
        # When an entire front does not fit in the space left we rely on the hypervolume
        # for this front
        # The remaining fronts are explicitely not chosen
        full = False
        for front in pareto_fronts:
            if len(chosen) + len(front) <= self.mu and not full:
                chosen += front
            elif mid_front is None and len(chosen) < self.mu:
                mid_front = front
                # With this front, we selected enough individuals
                full = True

        # Separate the mid front to accept only k individuals
        k = self.mu - len(chosen)
        if k > 0:
            # Reference point is chosen as the worst in each dimension +1
            # It is mostly arbitrary
            ref = numpy.array([c["loss"] for i, c in valid_candidates])
            ref = numpy.max(ref, axis=0) + 1

            # Remove sequentially k individuals
            for _ in range(len(mid_front) - k):
                idx = self.indicator([valid_candidates[i][1]["loss"] for i in mid_front], ref=ref)
                mid_front.pop(idx)

            chosen += mid_front

        return [valid_candidates[i][0] for i in chosen]

    def _update_internals(self, candidate):
        assert len(self.parents) == self.mu, "Invalid number of parents in MO-CMA-ES internal update"
        assert all("loss" in p for p in self.parents), "One parent has no loss in MO-CMA-ES internal update"
        assert all(p["loss"] is not None for p in self.parents), "Loss is None for a parent in MO-CMA-ES"
        p_idx = int(candidate["parent_idx"])
        chosen = self._select(self.parents + [candidate])
        candidate_is_chosen = self.mu in chosen

        # Only psucc and sigma of parent are update
        last_steps = list(self.sigmas)
        self.psucc[p_idx] = (1 - self.cp) * self.psucc[p_idx] + self.cp * candidate_is_chosen
        self.sigmas[p_idx] = self.sigmas[p_idx] * numpy.exp((self.psucc[p_idx] - self.ptarg) / (self.d * (1.0 - self.ptarg)))

        if candidate_is_chosen:
            if self.psucc[p_idx] < self.pthresh:
                pc = (1 - self.cc) * self.pc[p_idx] + numpy.sqrt(self.cc * (2 - self.cc)) * candidate["step"] / last_steps[p_idx]
                C = (1 - self.ccov) * self.C[p_idx] + self.ccov * numpy.outer(pc, pc)
            else:
                pc = (1 - self.cc) * self.pc[p_idx]
                C = (1 - self.ccov) * self.C[p_idx] + self.ccov * (numpy.outer(pc, pc) + self.cc * (2 - self.cc) * self.C[p_idx])

            # Replace the unselected parent parameters by those of the candidate
            not_chosen = set(range(self.mu + 1)) - set(chosen)
            assert len(not_chosen) == 1, "Invalid selection occured in MO-CMA-ES"
            not_chosen = list(not_chosen)[0]
            self.parents[not_chosen] = candidate
            self.psucc[not_chosen] = self.psucc[p_idx]
            self.sigmas[not_chosen] = self.sigmas[p_idx]
            self.pc[not_chosen] = pc
            self.A[not_chosen] = numpy.linalg.cholesky(C)
            self.C[not_chosen] = C

        # Update the dimensions where integer mutation is needed
        self.i_I_R[p_idx] = numpy.flatnonzero(2 * self.sigmas[p_idx] * numpy.diag(self.C[p_idx]) ** 0.5 < self.S_int)

    def _generate(self):
        # Select the parent at random from the non dominated set of parents
        losses = [c["loss"] for c in self.parents]
        ndom = argsortNondominated(losses, len(losses), first_front_only=True)
        idx = self.random_state.randint(0, len(ndom))
        # ndom contains the indices of the parents
        p_idx = ndom[idx]

        n_I_R = self.i_I_R[p_idx].shape[0]
        R_int = numpy.zeros(self.dim)

        # Mixed integer CMA-ES is developped for (mu/mu , lambda)
        # We have a (1 + 1) setting, thus we make the integer mutation probabilistic.
        # The integer mutation is lambda / 2 if all dimensions are integers or
        # min(lambda / 2 - 1, lambda / 10 + n_I_R + 1), minus 1 accounts for
        # the last new candidate getting its integer mutation from the last best
        # solution.
        if n_I_R == self.dim:
            p = 0.5
        else:
            p = min(0.5, 0.1 + n_I_R / self.dim)

        if n_I_R > 0 and self.random_state.rand() < p:
            Rp = numpy.zeros(self.dim)
            Rpp = numpy.zeros(self.dim)

            # Ri' has exactly one of its components set to one.
            # The Ri' are dependent in that the number of mutations for each coordinate
            # differs at most by one.
            j = self.random_state.choice(self.i_I_R[p_idx])
            Rp[j] = 1
            Rpp[j] = self.random_state.geometric(p=0.7 ** (1.0 / n_I_R)) - 1

            I_pm1 = (-1) ** self.random_state.randint(0, 2, self.dim)
            R_int = I_pm1 * (Rp + Rpp)

        y = numpy.dot(self.random_state.standard_normal(self.dim), self.A[p_idx].T)
        arz = self.parents[p_idx]["X"] + self.sigmas[p_idx] * y + self.S_int * R_int

        # Repair candidates outside [0, 1)
        arz_corr = numpy.clip(arz, 0, 1 - 1e-8)
        y_corr = ((arz_corr - arz) / self.sigmas[p_idx]) + y

        return arz_corr, y_corr , p_idx
