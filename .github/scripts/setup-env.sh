#!/bin/bash
set -e

# PYPI
echo $PYPIRC_B64 | base64 --decode > $HOME/.pypirc
