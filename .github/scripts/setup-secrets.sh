#!/bin/bash
set -e

mkdir -p ~/.gnupg ~/.gradle

echo $GPG_SECRET_B64 | base64 --decode > ~/.gnupg/secring.gpg
echo $GRADLE_PROPERTIES_B64 | base64 --decode > ~/.gradle/gradle.properties
