#!/bin/bash
set -e

mkdir -p ~/.gnupg ~/.gradle ~/.ssh

echo $GPG_SECRET_B64 | base64 --decode > ~/.gnupg/secring.gpg
echo $GRADLE_PROPERTIES_B64 | base64 --decode > ~/.gradle/gradle.properties
echo $PYPIRC_B64 | base64 --decode > ~/.pypirc
echo $SSH_CONFIG_B64 | base64 --decode > ~/.ssh/config
echo $SSH_IDRSA_B64 | base64 --decode > ~/.ssh/elastiknn-site
chmod 400 ~/.ssh/elastiknn-site
cat ~/.ssh/elastiknn-site | md5sum
cat ~/.ssh/config
ls -la ~/.ssh