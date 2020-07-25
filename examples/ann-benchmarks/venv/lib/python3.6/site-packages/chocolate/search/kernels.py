from sklearn.gaussian_process import kernels
import numpy

class ConditionalKernel(kernels.StationaryKernelMixin, kernels.Kernel):
    def __init__(self, space):
        self.space = space
        self.k = kernels.ConstantKernel() * kernels.RBF()

    def __call__(self, X, y=None, eval_gradient=False):

        if y is None:
            Xcond = numpy.array([list(self.space.isactive(x)) for x in X])
            cond = numpy.equal(Xcond[:, None], Xcond).all(axis=2).astype(int)
            return self.k(X) * cond
        else:
            Xcond = numpy.array([list(self.space.isactive(x)) for x in X])
            ycond = numpy.array([list(self.space.isactive(x)) for x in y])
            cond = numpy.equal(Xcond[:, None], ycond).all(axis=2).astype(int)
            return self.k(X, y) * cond

    def diag(self, X):
        return self.k.diag(X)
