#!/bin/bash
set -e

# SSH
mkdir -p $HOME/.ssh
echo $SSH_CONFIG_B64 | base64 --decode > $HOME/.ssh/config
echo $SSH_IDRSA_B64 | base64 --decode > $HOME/.ssh/elastiknn-site
chmod 400 $HOME/.ssh/elastiknn-site

# PYPI
echo $PYPIRC_B64 | base64 --decode > $HOME/.pypirc
