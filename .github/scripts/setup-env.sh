#!/bin/bash
set -e

mkdir -p $HOME/.gnupg $HOME/.gradle $HOME/.ssh

echo $GPG_SECRET_B64 | base64 --decode > $HOME/.gnupg/secring.gpg
echo $GRADLE_PROPERTIES_B64 | base64 --decode > $HOME/.gradle/gradle.properties
echo $PYPIRC_B64 | base64 --decode > $HOME/.pypirc
echo $SSH_CONFIG_B64 | base64 --decode > $HOME/.ssh/config
echo $SSH_IDRSA_B64 | base64 --decode > $HOME/.ssh/elastiknn-site
chmod 400 $HOME/.ssh/elastiknn-site
envsubst < .github/scripts/aws-credentials-template > ~/.aws/credentials
