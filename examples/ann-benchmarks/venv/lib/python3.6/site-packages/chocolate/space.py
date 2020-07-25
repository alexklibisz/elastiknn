"""This module provides common building blocks to define a search space.

Search spaces are defined using dictionaries, where the keys are the parameter
names and the values their distribution. For example, defining a two
parameter search space is done as follow ::

    space = {"x": uniform(-5, 5),
             "y": quantized_uniform(-2, 3, 0.5)}

A conditional search space can be seen as a tree, where each condition
defines a subtree. For example, in the next figure, three search spaces
are presented.

.. image:: /images/search-space-tree.png
   :width: 300px
   :align: center

The left tree is the simple two parameter search space defined earlier. The
middle tree defines a conditional search space with a single root condition.
Two subspaces exist in this search space, one when the condition is `a` the
other when the condition is `b`. Defining such a search space is done using
a list of dictionaries as follow ::

    space = [{"cond": "a", "x": uniform(-5, 5)},
             {"cond": "b", "y": quantized_uniform(-2, 3, 0.5)}]

The right most tree has two conditions one at its root and another one when
the root condition is `a`. It has a total of four subspaces. Defining such a
search space is done using a hierarchy of dictionaries as follow ::

    space = [{"cond": "a", "sub": {"c": {"x": uniform(-5, 5)},
                                   "d": {"z": log(-5, 5, 10)},
                                   "e": {"w": quantized_log(-2, 7, 1, 10)}}},
             {"cond": "b", "y": quantized_uniform(-2, 3, 0.5)}

Note that lists can only be used at the root of conditional search spaces,
sub-conditions must use the dictionary form. Moreover, it is not necessary to
use the same parameter name for root conditions. For example, the following
is a valid search space ::

    space = [{"cond": "a", "x": uniform(-5, 5)},
             {"spam": "b", "y": quantized_uniform(-2, 3, 0.5)}]

The only restriction is that each search space must have a unique combination
of conditional parameters and values, where conditional parameters have
non-distribution values. Finally, one and only one subspace can be defined
without condition as follow ::

    space = [{"x": uniform(-5, 5)},
             {"cond": "b", "y": quantized_uniform(-2, 3, 0.5)}]

If two or more subspaces share the same conditional key (set of parameters
and values) an :class:`AssertionError` will be raised uppon building the
search space specifying the erroneous key.
"""
from collections import OrderedDict, Mapping, Sequence
from itertools import chain, count, islice, product, combinations

import numpy

class _Constant(object):
    """Implements Chocolat constant value. his is used internally
    by other modules.
    """
    def __init__(self, value):
        self.value = value

class Distribution(object):
    """Base class for every Chocolate distributions."""
    def __len__(self):
        raise NotImplementedError

    def __getitem__(self, item):
        raise NotImplementedError

    def __ne__(self, other):
        return not (self == other)

class ContinuousDistribution(Distribution):
    """Base class for every Chocolate continuous distributions."""
    pass

class QuantizedDistribution(Distribution):
    """Base class for every Chocolate quantized distributions."""
    pass

class uniform(ContinuousDistribution):
    """Uniform continuous distribution.

    Representation of the uniform continuous distribution in the half-open
    interval :math:`[\\text{low}, \\text{high})`.

    Args:
        low: Lower bound of the distribution. All values will be
            greater or equal than low.
        high: Upper bound of the distribution.  All values will be
            lower than high.
    """
    def __init__(self, low, high):
        assert low < high, "Low must be lower than high"
        self.low = low
        self.high = high

    def __call__(self, x):
        """Transforms *x* a uniform number taken from the half-open continuous
        interval :math:`[0, 1)` to the represented distribution.

        Returns:
            The corresponding number in the half-open interval
            :math:`[\\text{low}, \\text{high})`.
        """
        return x * (self.high - self.low) + self.low

    def __repr__(self):
        return "uniform(low={}, high={})".format(self.low, self.high)

    def __eq__(self, other):
        return self.low == other.low and self.high == other.high


class quantized_uniform(QuantizedDistribution):
    """Uniform discrete distribution.

    Representation of the uniform continuous distribution in the half-open
    interval :math:`[\\text{low}, \\text{high})` with regular spacing between
    samples. If :math:`\\left\\lceil \\frac{\\text{high} - \\text{low}}{step}
    \\right\\rceil \\neq \\frac{\\text{high} - \\text{low}}{step}`, the last
    interval will have a different probability than the others. It is
    preferable to use :math:`\\text{high} = N \\times \\text{step} +
    \\text{low}` where :math:`N` is a whole number.

    Args:
        low: Lower bound of the distribution. All values will be
            greater or equal than low.
        high: Upper bound of the distribution.  All values will be
            lower than high.
        step: The spacing between each discrete sample.
    """
    def __init__(self, low, high, step):
        assert low < high, "Low must be lower than high"
        assert step > 0, "Step must be greater than 0"
        self.low = low
        self.high = high
        self.step = step

    def __call__(self, x):
        """Transforms *x*, a uniform number taken from the half-open continuous
        interval :math:`[0, 1)`, to the represented distribution.

        Returns:
            The corresponding number in the discrete half-open interval
            :math:`[\\text{low}, \\text{high})` alligned on step size. If the
            output number is whole, this method returns an :class:`int`
            otherwise a
            :class:`float`.
        """
        x += 1e-16  # handle floating point errors in floor
        v = numpy.floor((x * (self.high - self.low)) / self.step) * self.step + self.low
        if v.is_integer():
            return int(v)
        return v

    def __iter__(self):
        """Iterate over all possible values of this discrete distribution in
        the :math:`[0, 1)` space. This is the same as ::

            numpy.arange(0, 1, step / (high - low))

        """
        step = self.step / (self.high - self.low)
        for x in numpy.arange(0, 1, step):
            yield x

    def __getitem__(self, i):
        """Retrieve the ``i`` th value of this distribution in the
        :math:`[0, 1)` space.
        """
        return float(i) / ((self.high - self.low) / self.step)

    def __len__(self):
        """Get the number of possible values for this distribution.
        """
        return int(numpy.ceil((self.high - self.low) / self.step))

    def __repr__(self):
        return "quantized_uniform(low={}, high={}, step={})".format(self.low, self.high, self.step)

    def __eq__(self, other):
        return self.low == other.low and self.high == other.high and self.step == other.step


class log(uniform):
    """Logarithmic uniform continuous distribution.

    Representation of the logarithmic uniform continuous distribution in the
    half-open interval :math:`[\\text{base}^\\text{low},
    \\text{base}^\\text{high})`.

    Args:
        low: Lower bound of the distribution. All values will be
            greater or equal than :math:`\\text{base}^\\text{low}`.
        high: Upper bound of the distribution.  All values will be
            lower than :math:`\\text{base}^\\text{high}`.
        base: Base of the logarithmic function.
    """
    def __init__(self, low, high, base):
        super(log, self).__init__(low, high)
        assert base > 0, "Base must be larger than 0"
        assert base != 1, "Base cannot equal 1"
        self.base = base

    def __call__(self, x):
        """Transforms *x*, a uniform number taken from the half-open continuous
        interval :math:`[0, 1)`, to the represented distribution.

        Returns:
            The corresponding number in the discrete half-open interval
            :math:`[\\text{base}^\\text{low}, \\text{base}^\\text{high})`
            alligned on step size. If the output number is whole, this
            method returns an :class:`int` otherwise a :class:`float`.
        """
        return self.base**(super(log, self).__call__(x))

    def __repr__(self):
        return "log(low={}, high={}, base={})".format(self.low, self.high, self.base)

    def __eq__(self, other):
        return self.low == other.low and self.high == other.high and self.base == other.base


class quantized_log(quantized_uniform):
    """Logarithmic uniform discrete distribution.

    Representation of the logarithmic uniform discrete distribution in the
    half-open interval :math:`[\\text{base}^\\text{low},
    \\text{base}^\\text{high})`. with regular spacing between sampled
    exponents.

    Args:
        low: Lower bound of the distribution. All values will be
            greater or equal than :math:`\\text{base}^\\text{low}`.
        high: Upper bound of the distribution.  All values will be
            lower than :math:`\\text{base}^\\text{high}`.
        step: The spacing between each discrete sample exponent.
        base: Base of the logarithmic function.
    """
    def __init__(self, low, high, step, base):
        super(quantized_log, self).__init__(low, high, step)
        assert base > 0, "Base must be larger than 0"
        assert base != 1, "Base cannot equal 1"
        self.base = base

    def __call__(self, x):
        """Transforms *x*, a uniform number taken from the half-open
        continuous interval :math:`[0, 1)`, to the represented distribution.

        Returns:
            The corresponding number in the discrete half-open interval
            :math:`[\\text{base}^\\text{low}, \\text{base}^\\text{high})`
            alligned on step size. If the output number is whole, this
            method returns an :class:`int` otherwise a :class:`float`.
        """
        x += 1e-16  # handle floating point errors in floor
        v = numpy.float(self.base**(super(quantized_log, self).__call__(x)))
        if v.is_integer():
            return int(v)
        return v

    def __repr__(self):
        return "quantized_log(low={}, high={}, step={}, base={})".format(self.low, self.high, self.step, self.base)

    def __eq__(self, other):
        return self.low == other.low and self.high == other.high and self.step == other.step and self.base == other.base

class choice(quantized_uniform):
    """Uniform choice distribution between non-numeric samples.

    Args:
        values: A list of choices to choose uniformly from.
    """
    def __init__(self, values):
        assert len(values) > 0, "Choices must at least have one value"
        self.values = list(values)
        super(choice, self).__init__(low=0, high=len(self.values), step=1)

    def __call__(self, x):
        """Transforms *x*, a uniform number taken from the half-open
        continuous interval :math:`[0, 1)`, to the represented distribution.

        Returns:
            The corresponding choice from the entered values.
        """
        assert x < 1, "Choices must lie in the half-open interval [0, 1)"
        return self.values[int(super(choice, self).__call__(x))]

    def __repr__(self):
        return "choice({})".format(self.values)

    def __eq__(self, other):
        return self.values == other.values


class Space(object):
    """Representation of the search space.

    Encapsulate a multidimentional search space defined on various
    distributions. Remind that order in standard python dictionary is
    undefined, thus the keys of the input dictionaries are
    :func:`sorted` and put in :class:`OrderedDict` s for reproductibility.

    Args:
        spaces: A dictionary or list of dictionaries of parameter names to
            their distribution. When a list of multiple dictionaries is
            provided, the structuring elements of these items must define a
            set of unique choices. Structuring elements are defined using
            non-distribution values. See examples below.

    Raises:
        AssertionError: When two keys at the same level are equal.

    An instance of a space is a callable object wich will return a valid
    parameter set provided a vector of numbers in the half-open uniform
    distribution :math:`[0, 1)`.

    The number of distinc dimensions can be queried with the :func:`len`
    function. When a list of dictionaries is provided, this choice constitute
    the first dimension and each subsequent conditional choice is also a
    dimension.

    Examples:
        Here is how a simple search space can be defined and the parameters
        can be retrieved  ::

            In [2]: s = Space({"learning_rate": uniform(0.0005, 0.1),
                               "n_estimators" : quantized_uniform(1, 11, 1)})

            In [3]: s([0.1, 0.7])
            Out[3]: {'learning_rate': 0.01045, 'n_estimators': 8}

        A one level conditional multidimentional search space is defined using
        a list of dictionaries. Here the choices are a SMV with linear kernel
        and a K-nearest neighbor as defined by the string values. Note the use
        of class names in the space definition. ::

            In [2]: from sklearn.svm import SVC

            In [3]: from sklearn.neighbors import KNeighborsClassifier

            In [4]: s = Space([{"algo": SVC, "kernel": "linear",
                                    "C": log(low=-3, high=5, base=10)},
                               {"algo": KNeighborsClassifier,
                                    "n_neighbors": quantized_uniform(low=1, high=20, step=1)}])

        The number of dimensions of such search space can be retrieved with
        the :func:`len` function. ::

            In [5]: len(s)
            Out[5]: 3

        As in the simple search space a valid parameter set can be retrieved
        by querying the space object with a vector of length equal to the full
        search space. ::

            In [6]: s([0.1, 0.2, 0.3])
            Out[6]:
            {'C': 0.039810717055349734,
             'algo': sklearn.svm.classes.SVC,
             'kernel': 'linear'}

            In [7]: s([0.6, 0.2, 0.3])
            Out[7]:
            {'algo': sklearn.neighbors.classification.KNeighborsClassifier,
             'n_neighbors': 6}

        Internal conditions can be modeled using nested dictionaries. For
        example, the SVM from last example can have different kernels. The
        next search space will share the ``C`` parameter amongst all SVMs, but
        will branch on the kernel type with their individual parameters. ::

            In [2]: s = Space([{"algo": "svm",
                                "C": log(low=-3, high=5, base=10),
                                "kernel": {"linear": None,
                                            "rbf": {"gamma": log(low=-2, high=3, base=10)}}},
                               {"algo": "knn",
                                    "n_neighbors": quantized_uniform(low=1, high=20, step=1)}])

            In [3]: len(s)
            Out[3]: 5

            In [4]: x = [0.1, 0.2, 0.7, 0.4, 0.5]

            In [5]: s(x)
            Out[5]: {'C': 0.039810717055349734, 'algo': 'svm', 'gamma': 1.0, 'kernel': 'rbf'}

    """
    def __init__(self, spaces):
        if isinstance(spaces, Mapping):
            spaces = [spaces]

        self.spaces = OrderedDict()
        self.constants = list()
        for subspace in spaces:
            ts_key = list()
            ts_space = OrderedDict()

            for k, v in sorted(subspace.items()):
                if k == "":# or k == "_subspace":
                    raise RuntimeError("'{}' is not a valid parameter name".format(k))

                if isinstance(v, Distribution):
                    ts_space[k] = v
                elif isinstance(v, Mapping):
                    cond_subspaces = list()

                    try:
                        sorted_v = sorted(v.items())
                    except TypeError as e:
                        sorted_v = sorted(v.items(), key=str)

                    for sub_k, sub_v in sorted_v:
                        s = {k: sub_k}
                        if isinstance(sub_v, Mapping):
                            s.update(sub_v)
                        elif sub_v is not None:
                            s[sub_k] = sub_v
                        cond_subspaces.append(s)

                    ts_space[k] = Space(cond_subspaces)
                elif isinstance(v, _Constant):
                    self.constants.append((k, v.value))
                else:
                    ts_key.append((k, v))

            ts_key = tuple(ts_key)
            assert ts_key not in self.spaces, "Duplicate conditiona key {} found in Space".format(ts_key)
            self.spaces[ts_key] = ts_space

            if len(self.spaces) > 1:
                # print(list(self.spaces.keys()))
                # assert all(self.spaces.keys()), "Empty subspace keys are not allowed in conditional search spaces."
                self.subspace_choice = quantized_uniform(low=0, high=len(self.spaces), step=1)

    def __len__(self):
        # We have a single level of choices if this is a structured spce
        ndims = 1 if len(self.spaces) > 1 else 0
        # Within each subspace, every non-structuring key is a dimension
        # A structuring key is not a chocolate.Distribution
        for subspace in self.spaces.values():
            for v in subspace.values():
                if isinstance(v, Space):
                    ndims += len(v)
                elif isinstance(v, Distribution):
                    ndims += 1

        return ndims

    def __call__(self, x):
        out = dict()
        assert len(self) == len(x), "Space and vector dimensions missmatch {} != {}".format(len(self), len(x))
        iter_x = iter(x)

        space_idx = 0
        if len(self.spaces) > 1:
            space_idx = self.subspace_choice(numpy.clip(next(iter_x), 0, 0.9999))

        subspace_key = list(self.spaces.keys())[space_idx]

        for key, subspace in self.spaces.items():
            for k, v in subspace.items():
                if isinstance(v, Distribution):
                    xi = next(iter_x)
                elif isinstance(v, Space):
                    xi = [next(iter_x) for _ in range(len(v))]

                if len(self.spaces) == 1 or subspace_key == key:
                    if isinstance(v, Distribution):
                        out[k] = v(xi)
                    elif isinstance(v, Space):
                        out.update(**v(xi))
                    else:
                        raise TypeError("Oops something went wrong!")

        out.update(subspace_key)
        out.update(self.constants)
        return out

    def isactive(self, x):
        """Checks within conditional subspaces if, with the given vector, a
        parameter is active or not.

        Args:
            x: A vector of numbers in the half-open uniform
                distribution :math:`[0, 1)`.

        Returns:
            A list of booleans telling is the parameter is active or not.

        Example:
            When using conditional spaces it is often necessary to assess
            quickly what dimensions are active according to a given vector.
            For example, with the following conditional space ::

                In [2]: s = Space([{"algo": "svm",
                                    "C": log(low=-3, high=5, base=10),
                                    "kernel": {"linear": None,
                                                "rbf": {"gamma": log(low=-2, high=3, base=10)}}},
                                   {"algo": "knn",
                                        "n_neighbors": quantized_uniform(low=1, high=20, step=1)}])
                In [3]: s.names()
                Out[3]:
                ['_subspace',
                 'algo_svm_C',
                 'algo_svm_kernel__subspace',
                 'algo_svm_kernel_kernel_rbf_gamma',
                 'algo_knn_n_neighbors']

                In [4]: x = [0.1, 0.2, 0.7, 0.4, 0.5]

                In [5]: s(x)
                Out[5]: {'C': 0.039810717055349734, 'algo': 'svm', 'gamma': 1.0, 'kernel': 'rbf'}

                In [6]: s.isactive(x)
                Out[6]: [True, True, True, True, False]

                In [6]: x = [0.6, 0.2, 0.7, 0.4, 0.5]

                In [8]: s(x)
                Out[8]: {'algo': 'knn', 'n_neighbors': 10}

                In [9]: s.isactive(x)
                Out[9]: [True, False, False, False, True]

        """
        assert len(self) == len(x), "Space and vector dimensions missmatch {} != {}".format(len(self), len(x))

        out = []
        iter_x = iter(x)

        if len(self.spaces) > 1:
            space_idx = self.subspace_choice(numpy.clip(next(iter_x), 0, 0.99999))
            subspace_key = list(self.spaces.keys())[space_idx]
            out.append(True)

        for key, subspace in self.spaces.items():
            for k, v in subspace.items():
                if isinstance(v, Distribution):
                    xi = next(iter_x)
                elif isinstance(v, Space):
                    xi = [next(iter_x) for _ in range(len(v))]

                if len(self.spaces) == 1 or subspace_key == key:
                    if isinstance(v, Distribution):
                        out.append(True)
                    elif isinstance(v, Space):
                        out.extend(v.isactive(xi))
                    else:
                        raise TypeError("Unexpected type {} in space".format(type(v)))
                else:
                    if isinstance(v, Distribution):
                        out.append(False)
                    elif isinstance(v, Space):
                        out.extend([False] * len(xi))
                    else:
                        raise TypeError("Unexpected type {} in space".format(type(v)))

        return out

    def names(self, unique=True):
        """Returns unique sequential names meant to be used as database column
        names.

        Args:
            unique: Whether or not to return unique mangled names. Subspaces will
                still be mangled.

        Examples:
            If the length of the space is 2 as follow ::

                In [2]: s = Space({"learning_rate": uniform(0.0005, 0.1),
                                   "n_estimators" : quantized_uniform(1, 11, 1)})

                In [3]: s.names()
                Out[3]: ['learning_rate', 'n_estimators']

            While in conditional spaces, if the length of the space is 5 (one
            for the choice od subspace and four independent parameters) ::


                In [4]: s = Space([{"algo": "svm", "kernel": "linear",
                                        "C": log(low=-3, high=5, base=10)},
                                   {"algo": "svm", "kernel": "rbf",
                                        "C": log(low=-3, high=5, base=10),
                                        "gamma": log(low=-2, high=3, base=10)},
                                   {"algo": "knn",
                                        "n_neighbors": quantized_uniform(low=1, high=20, step=1)}])

                In [5]: s.names()
                Out[5]:
                ['_subspace',
                 'algo_svm_kernel_linear_C',
                 'algo_svm_kernel_rbf_C',
                 'algo_svm_kernel_rbf_gamma',
                 'algo_knn_n_neighbors']

            When using methods or classes as parameter values for conditional
            choices the output might be a little bit more verbose, however the
            names are still there. ::

                In [6]: s = Space([{"algo": SVC,
                                    "C": log(low=-3, high=5, base=10),
                                    "kernel": {"linear": None,
                                                "rbf": {"gamma": log(low=-2, high=3, base=10)}}},

                                   {"algo": KNeighborsClassifier,
                                        "n_neighbors": quantized_uniform(low=1, high=20, step=1)}])

                In [7]: s.names()
                Out[7]:
                ['_subspace',
                 'algo_<class sklearn_svm_classes_SVC>_C',
                 'algo_<class sklearn_svm_classes_SVC>_kernel__subspace',
                 'algo_<class sklearn_svm_classes_SVC>_kernel_kernel_rbf_gamma',
                 'algo_<class sklearn_neighbors_classification_KNeighborsClassifier>_n_neighbors']
        """
        names = list()
        if len(self.spaces) > 1:
            names.append("_subspace")

        for key, subspace in self.spaces.items():
            for k, v in subspace.items():
                prefix = "{}_".format("_".join(str(ni) for ni in chain(*key))) if key else ""
                prefix = prefix.replace("\"", "")
                prefix = prefix.replace("'", "")
                prefix = prefix.replace(".", "_")
                if isinstance(v, Distribution):
                    if unique:
                        names.append("{}{}".format(prefix, k))
                    else:
                        names.append(k)
                elif isinstance(v, Space):
                    for n in v.names(unique):
                        if unique or n.endswith("_subspace"):
                            names.append("{}{}_{}".format(prefix, k, n))
                        else:
                            names.append(n)
                else:
                    raise TypeError("Unexpected type {} inspace".format(type(v)))

        return names

    def steps(self):
        """Returns the steps size between each element of the space
        dimensions. If a variable is continuous the returned stepsize is :data:`None`.
        """
        steps = list()
        if len(self.spaces) > 1:
            steps.append(self.subspace_choice.step / (self.subspace_choice.high - self.subspace_choice.low))

        for subspace in self.spaces.values():
            for v in subspace.values():
                if isinstance(v, QuantizedDistribution):
                    steps.append(v.step / (v.high - v.low))
                elif isinstance(v, Space):
                    steps.extend(v.steps())
                else:
                    steps.append(None)

        return steps

    def isdiscrete(self):
        """Returns whether or not this search space has only discrete
        dimensions.
        """
        for subspace in self.spaces.values():
            for v in subspace.values():
                if isinstance(v, ContinuousDistribution):
                    return False
                elif isinstance(v, Space) and not v.isdiscrete():
                    return False

        return True

    def subspaces(self):
        """Returns every valid combinaition of conditions of the tree-
        structured search space. Each combinaition is a list of length equal
        to the total dimensionality of this search space. Active dimensions
        are either a fixed value for conditions or a :class:`Distribution` for
        optimizable parameters. Inactive dimensions are :data:`None`.

        Example:
            The following search space has 3 possible subspaces

            ::

                In [2]: s = Space([{"algo": "svm",
                                    "C": log(low=-3, high=5, base=10),
                                    "kernel": {"linear": None,
                                                "rbf": {"gamma": log(low=-2, high=3, base=10)}}},
                                   {"algo": "knn",
                                        "n_neighbors": quantized_uniform(low=1, high=20, step=1)}])

                In [3]: s.names()
                Out[3]:
                ['_subspace',
                 'algo_svm_C',
                 'algo_svm_kernel__subspace',
                 'algo_svm_kernel_kernel_rbf_gamma',
                 'algo_knn_n_neighbors']

                In [4]: s.subspaces()
                Out[4]:
                [[0.0, log(low=-3, high=5, base=10), 0.0, None, None],
                 [0.0, log(low=-3, high=5, base=10), 0.5, log(low=-2, high=3, base=10), None],
                 [0.5, None, None, None, quantized_uniform(low=1, high=20, step=1)]]
        """
        subspaces, _ = self._subspaces()
        return subspaces

    def _subspaces(self):
        branches = list()
        indices = list()
        if len(self.spaces) > 1:
            position = 1
        else:
            position = 0

        for i, (key, subspace) in enumerate(self.spaces.items()):
            branch = [None] * len(self)
            idx = list()

            if len(self.spaces) > 1:
                step = self.subspace_choice.step / (self.subspace_choice.high - self.subspace_choice.low)
                branch[0] = i * step
                idx.append(0)

            conditionals = list()
            conditional_idx = list()
            for k, v in subspace.items():
                if not isinstance(v, Space):
                    branch[position] = v
                    idx.append(position)
                    position += 1

                else:
                    cond_spaces, cond_indices = v._subspaces()
                    conditionals.append(cond_spaces)
                    conditional_idx.append([[position + j for j in s] for s in cond_indices])
                    if any(cond_indices):
                        position += max(max(s) for s in cond_indices if s) + 1


            if len(conditionals) == 0:
                branches.append(branch)
                indices.append(idx)
            else:
                for elements, indx in zip(product(*conditionals), product(*conditional_idx)):
                    cond_branch = branch.copy()
                    cond_indices = idx.copy()
                    for e, j in zip(elements, indx):
                        # Remove Nones from underlying spaces
                        e = [ei for ei in e if ei is not None]
                        for ei, ji in zip(e, j):
                            cond_branch[ji] = ei
                            cond_indices.append(ji)
                    branches.append(cond_branch)
                    indices.append(cond_indices)

        return branches, indices

    def __eq__(self, other):
        if isinstance(other, Space):
            return self.spaces == other.spaces
        return False

    def __ne__(self, other):
        return not (self == other)


if __name__ == "__main__":
    from sklearn.svm import SVC, LinearSVC
    from sklearn.neighbors import KNeighborsClassifier

    space1 = {"a": uniform(1, 2),
              "b": {"c": {"c1": quantized_log(0, 5, 1, 10)},
                     "d": {"d1": quantized_log(0, 5, 1, 2)}}}

    space2 = {"a": uniform(1, 2),
              "b": {"c": {"c1": quantized_log(0, 5, 1, 10)},
                     "d": {"d1": quantized_log(0, 5, 1, 2)}}}

    space3 = {"a": uniform(1, 2),
              "b": {"c": {"c1": quantized_log(0, 5, 1, 10)},
                     "d": {"d2": quantized_log(0, 5, 1, 2)}}}

    space4 = {"a": uniform(1, 2),
              "b": {"c": {"c1": quantized_log(0, 5, 1, 10)},
                     "d": {"d1": quantized_log(0, 5, 1, 8)}}}

    print(Space(space1) == Space(space2))
    print(Space(space1) == Space(space3))
    print(Space(space1) == Space(space4))

    space = {"initial_learning_rate": choice([0.0005]),
             "learning_rate_decay": choice([0.0004]),
             "keep_prob": choice([0.7, 0.9]),
             "nout_c1": quantized_log(low=0, high=3, step=1, base=2),
             "nout_c2": quantized_log(low=0, high=3, step=1, base=2),
             "nout_fc1": quantized_log(low=0, high=3, step=1, base=2),
             "nout_fc2": quantized_log(low=0, high=3, step=1, base=2),
             "act_fn": choice(["elu", "relu"])}

    s = Space(space)
    print(len(s))
    print(s.steps())
    print(s.names())
    print(s.isdiscrete())
    for subspace in s.subspaces():
        print("*", subspace)

    x = [0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8]
    print(s(x))
    print(s.isactive(x))



    print("="*12)
    space = [{"algo": SVC, "kernel": "rbf", "C": uniform(low=0.001, high=10000), "gamma": uniform(low=0, high=1)},
             {"algo": SVC, "kernel": "linear", "C": uniform(low=0.001, high=10000)},
             {"algo": KNeighborsClassifier, "n_neighbors": quantized_uniform(low=1, high=20, step=1)},
             {"algo": "cnn", "num_layers": 8, "n_units": quantized_log(low=5, high=8, step=1, base=2)},
             {"algo": "cnn", "num_layers": 4, "n_units": quantized_log(low=5, high=12, step=1, base=2)}]

    s = Space(space)
    print(len(s))
    print(s.steps())
    print(s.names())
    print(s.isdiscrete())
    for subspace in s.subspaces():
        print("*", subspace)

    x = [0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7]
    print(s(x))
    print(s.isactive(x))

    print("="*12)
    space = [{"algo": "svm",
              "C": log(low=-3, high=5, base=10),
              "kernel": {"linear": None,
                          "rbf": {"gamma": log(low=-2, high=3, base=10)}}},

             {"algo": "knn",
                  "n_neighbors": quantized_uniform(low=1, high=20, step=1)}]

    s = Space(space)
    print(len(s))
    print(s.steps())
    print(s.names())
    print(s.isdiscrete())
    for subspace in s.subspaces():
        print("*", subspace)

    x = [0.1, 0.2, 0.7, 0.4, 0.5]
    print(s(x))
    print(s.isactive(x))

    print("="*12)
    space = {"algo": {"svm": {"C": log(low=-3, high=5, base=10),
                              "kernel": {"linear": None,
                                          "rbf": {"gamma": log(low=-2, high=3, base=10)}},
                              "cond2": {"aa": None,
                                          "bb": {"abc": uniform(low=-1, high=1)}}},
                       "knn": {"n_neighbors": quantized_uniform(low=1, high=20, step=1)}}}

    s = Space(space)
    print(len(s))
    print(s.steps())
    print(s.names())
    print(s.isdiscrete())
    for subspace in s.subspaces():
        print("*", subspace)

    x = [0.5, 0.0, 0.5, 0.5, 0.3, 0.5, 0.2]
    print(s(x))
    print(s.isactive(x))

    print("="*12)
    space = {"x1": quantized_uniform(-5, 10, 2),
             "cond": {"log": {"x2": quantized_log(0, 2, 0.1, 2)},
                       "uni": {"x2": quantized_uniform(0, 15, 2)}}}

    s = Space(space)
    print(len(s))
    print(s.steps())
    print(s.names())
    print(s.isdiscrete())
    for subspace in s.subspaces():
        print("*", subspace)

    x = [0.1, 0.2, 0.7, 0.4]
    print(s(x))
    print(s.isactive(x))


    print("="*12)
    space = {"algo": {SVC: {"gamma": log(low=-9, high=3, base=10)},
                              "kernel": {"rbf": None,
                                          "poly": {"degree": quantized_uniform(low=1, high=5, step=1),
                                                    "coef0": uniform(low=-1, high=1)}},
                       LinearSVC: {"penalty": choice(["l1", "l2"])}},
             "C": log(low=-2, high=10, base=10)}

    s = Space(space)
    print(len(s))
    print(s.steps())
    print(s.names())
    print(s.isdiscrete())
    for subspace in s.subspaces():
        print("*", subspace)


    print("="*12)
    space = [{"algo": {SVC: {"gamma": log(low=-9, high=3, base=10),
                               "kernel": {"rbf": None,
                                           "poly": {"degree": quantized_uniform(low=1, high=5,  step=1),
                                                     "coef0": uniform(low=-1, high=1)}}},
                        LinearSVC: {"penalty": choice(["l1", "l2"])}},
              "C": log(low=-2, high=10, base=10)},

             {"type": "an_other_optimizer", "param": uniform(low=-1, high=1)}]

    s = Space(space)
    print(len(s))
    print(s.steps())
    print(s.names())
    print(s.isdiscrete())
    for subspace in s.subspaces():
        print("*", subspace)
