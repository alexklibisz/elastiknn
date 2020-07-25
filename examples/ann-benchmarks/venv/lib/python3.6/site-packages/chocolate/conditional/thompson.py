from collections import Sequence
from itertools import chain

import numpy

from ..base import SearchAlgorithm
from ..connection.splitter import ConnectionSplitter, split_space, transform_suboutput


class ThompsonSampling(SearchAlgorithm):
    """Conditional subspaces exploration strategy. 
    
    Thompson sampling wrapper to sample subspaces proportionally to their
    estimated quality. Each subspace of a conditional search space will be treated
    independently. This version uses an estimated moving average for the reward and
    forgets the reward of unselected subspaces allowing to model the dynamics
    of the underlying optimizers. Thompson sampling for Bernoulli bandit is described
    in [Chapelle2011]_.

    Args:
        algo: An algorithm to sample/search each subspace.
        connection: A database connection object.
        space: The conditional search space to explore.
        crossvalidation: A cross-validation object that handles experiment
            repetition.
        clear_db: If set to :data:`True` and a conflict arise between the
            provided space and the space in the database, completely clear the
            database and insert set the space to the provided one.
        random_state: An instance of :class:`~numpy.random.RandomState`, an
            object to initialize the internal random state with, or None, in
            which case the global numpy random state is used.
        gamma: Estimated moving average learning rate. The higher, the faster
            will react the bandit to a change of best arm. Should be in [0, 1].
        epsilon: Forget rate for unselected arms. Th higher, the faster unselected
            arms will fallback to a symmetric distribution. Should be in [0, 1].
        algo_params: A dictionary of the parameters to pass to the algorithm.


    .. [Chapelle2011] O. Chapelle and L. Li, "An empirical evaluation of
       Thompson sampling", in Advances in Neural Information Processing
       Systems 24 (NIPS), 2011.
    """
    def __init__(self, algo, connection, space, crossvalidation=None, clear_db=False, random_state=None,
                 gamma=0.9, epsilon=0.05, algo_params=None):
        super(ThompsonSampling, self).__init__(connection, space, crossvalidation, clear_db)
        self.arms = list()
        for i, cond_space in enumerate(split_space(self.space)):
            connection = ConnectionSplitter(self.conn, i, "_arm_id")
            self.arms.append(algo(connection, cond_space, **(algo_params or {})))

        self.gamma = gamma
        self.epsilon = epsilon

        if isinstance(random_state, numpy.random.RandomState):
            self.random_state = random_state
        elif random_state is None:
            self.random_state = numpy.random
        else:
            self.random_state = numpy.random.RandomState(random_state)

    def _init(self):
        self.emar = [0.5] * len(self.arms)
        self.success = list(self.emar)
        self.failure = [1.0 - e for e in self.emar]
        self.arm_idx = list(range(len(self.arms)))

    def _update_arms(self, arm, reward):
        """Update knowledge with reward."""
        # Update selected arm statistics
        self.emar[arm] = (1. - self.gamma) * self.emar[arm] + self.gamma * reward
        self.success[arm] += self.emar[arm]
        self.failure[arm] += 1. - self.emar[arm]

        # Forget past rewards for unselected arms
        for i in chain(range(arm), range(arm + 1, len(self.emar))):
            self.emar[i] = (1. - self.epsilon) * self.emar[i] + self.epsilon * 0.5
            self.success[i] *= (1.0 - self.epsilon)
            self.failure[i] *= (1.0 - self.epsilon)

    def _remove_arm(self, i):
        self.emar.pop(i)
        self.success.pop(i)
        self.failure.pop(i)

    def _select_arm(self):
        """Select and return the next arm (as index) to play."""
        return numpy.argmax([self.random_state.beta(s + 1, f + 1) for s, f in zip(self.success, self.failure)])

    @property
    def _active_arms(self):
        """Returns a list of active arms"""
        return [self.arms[idx] for idx in self.arm_idx]

    def _next(self, token=None):
        """Retrieve the next point to evaluate based on available data in the
        database. Each time :meth:`next` is called, the algorithm will reinitialize
        it-self based on the data in the database.

        Returns:
            A tuple containing a unique token and a fully qualified parameter set.
        """
        self._init()

        losses = list()
        for entry in self.conn.all_results():
            arm_id = entry.get("_arm_id", None)
            loss = entry.get("_loss", None)
            if arm_id and loss:
                idx = self.arm_idx.index(arm_id)
                success = 1
                if losses:
                    success = loss < numpy.median(losses)
                self._update_arms(idx, success)
                losses.append(loss)

        # Remove inactive arms (those that exhausted all parameter possibilities)
        while True:
            idx = self.arm_idx[self._select_arm()]
            try:
                token, params = self.arms[idx]._next(token)
            except StopIteration:
                self._remove_arm(idx)
                if len(self._active_arms) == 0:
                    raise
            else:
                token.update({"_arm_id": idx})
                return token, transform_suboutput(params, self.space)
