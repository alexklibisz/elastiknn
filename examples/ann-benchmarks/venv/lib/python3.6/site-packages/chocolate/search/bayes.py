import numpy
from scipy.optimize import minimize
from scipy.stats import norm
from sklearn import gaussian_process

from . import kernels
from ..base import SearchAlgorithm


class Bayes(SearchAlgorithm):
    """Bayesian minimization method with gaussian process regressor.

    This method uses scikit-learn's implementation of gaussian processes
    with the addition of a conditional kernel when the provided space is conditional
    [Lévesque2017]_. Two acquisition functions are made available, the Upper
    Confidence Bound (UCB) and the Expected Improvement (EI).

    Args:
        connection: A database connection object.
        space: the search space to explore with only discrete dimensions.
        crossvalidation: A cross-validation object that handles experiment
            repetition.
        clear_db: If set to :data:`True` and a conflict arise between the
            provided space and the space in the database, completely clear the
            database and set set the space to the provided one.
        n_bootstrap: The number of random iteration done before using gaussian processes.
        utility_function (str): The acquisition function used for the bayesian optimization.
            Two functions are implemented: "ucb" and "ei".
        kappa: Kappa parameter for the UCB acquisition function.
        xi: xi parameter for the EI acquisition function.

    .. [Lévesque2017] Lévesque, Durand, Gagné and Sabourin. Bayesian Optimization for
       Conditional Hyperparameter Spaces. 2017
    """
    def __init__(self, connection, space, crossvalidation=None, clear_db=False, n_bootstrap=10, utility_function="ucb", kappa=2.756,
                 xi=0.1):
        super(Bayes, self).__init__(connection, space, crossvalidation, clear_db)
        self.k = None
        if len(self.space.subspaces()) > 1:
            self.k = kernels.ConditionalKernel(self.space)
        self.n_bootstrap = n_bootstrap
        if self.n_bootstrap <= 0:
            raise ValueError("Bayes algorithm needs at least a point to boostrap.")
        if utility_function == "ucb":
            self.utility = self._ucb
            self.utility_kws = {"kappa": kappa}
        elif utility_function == "ei":
            self.utility = self._ei
            self.utility_kws = {"xi": xi}

        self.random_state = numpy.random.RandomState()

    def _next(self, token=None):
        X, Xpending, y = self._load_database()
        token = token or {}
        token.update({"_chocolate_id": len(X) + len(Xpending)})
        if len(X) < self.n_bootstrap:
            out = self.random_state.random_sample((len(list(self.space.names())),))
        else:
            gp, y = self._fit_gp(X, Xpending, y)
            out = self._acquisition(gp, y)

        # Signify the first point to others using loss set to None
        # Transform to dict with parameter names
        entry = {str(k): v for k, v in zip(self.space.names(), out)}
        entry.update(token)
        self.conn.insert_result(entry)
        return token, self.space(out)

    def _fit_gp(self, X, Xpending, y):
        gp = gaussian_process.GaussianProcessRegressor(kernel=self.k)
        X = numpy.array([[elem[k] for k in self.space.names()] for elem in X])
        Xpending = numpy.array([[elem[k] for k in self.space.names()] for elem in Xpending])
        y = numpy.array(y)
        gp.fit(X, y)
        if Xpending.size:
            y_predict = gp.predict(Xpending)
            X = numpy.concatenate([X, Xpending])
            y = numpy.concatenate([y, y_predict])
            gp.fit(X, y)
        return gp, y

    def _load_database(self):
        dbdata = self.conn.all_results()
        X, y, Xpending = [], [], []
        for elem in dbdata:
            if "_loss" in elem and elem["_loss"] is not None:
                X.append(elem)
                y.append(-elem["_loss"])  # Negative because we maximize
            else:
                Xpending.append(elem)
        return X, Xpending, y

    def _acquisition(self, gp, y):
        y_max = y.max()
        bounds = numpy.array([[0., 0.999999] for _ in self.space.names()])
        max_acq = None  #y_max
        x_max = None
        x_seeds = numpy.random.uniform(bounds[:, 0], bounds[:, 1],
                                       size=(100, bounds.shape[0]))
        for x_try in x_seeds:
            # Find the minimum of minus the acquisition function
            x_try[[not i for i in self.space.isactive(x_try)]] = 0

            res = minimize(lambda x: -self.utility(x.reshape(1, -1), gp=gp, y_max=y_max,
                                                   **self.utility_kws),
                           x_try.reshape(1, -1),
                           bounds=bounds,
                           method="L-BFGS-B")

            # Store it if better than previous minimum(maximum).
            if max_acq is None or -res.fun[0] >= max_acq:
                x_max = res.x
                max_acq = -res.fun[0]
        assert x_max is not None, "This is an invalid case"
        return x_max

    @staticmethod
    def _ucb(x, gp, kappa, **kwargs):
        mean, std = gp.predict(x, return_std=True)
        return mean + kappa * std

    @staticmethod
    def _ei(x, gp, y_max, xi, **kwargs):
        mean, std = gp.predict(x, return_std=True)
        z = (mean - y_max - xi) / std
        return (mean - y_max - xi) * norm.cdf(z) + std * norm.pdf(z)
