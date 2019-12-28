from setuptools import setup

with open("requirements.txt") as fp:
    reqs = fp.read().strip().split("\n")

setup(
    name='elastiknn-client',
    version='0.0.0',
    packages=["elastiknn", "scalapb"],
    author='Alex Klibisz',
    author_email='aklibisz@gmail.com',
    url='https://github.com/alexklibisz/elastiknn',
    include_package_data=True,
    install_requires=reqs
)
