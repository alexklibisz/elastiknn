#!/bin/bash
set -e

echo "*************************************"
echo "** Seting Max Virtual Memory Areas **"
echo "*************************************"
sudo sysctl -w vm.max_map_count=262144

echo "*******************"
echo "** Installing gh **"
echo "*******************"
sudo apt-get -qq update
sudo apt-get -qq install gh
which gh

echo "***********************"
echo "** Installing Docker **"
echo "***********************"
sudo apt-get -qq install -y ca-certificates curl
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc
echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu \
  $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | \
  sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
sudo apt-get -qq update
sudo apt-get -qq install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo usermod -aG docker $USER

echo "*********************"
echo "** Installing Task **"
echo "*********************"
sudo apt-get -qq install -y snapd
sudo snap install --classic task
which task

echo "********************"
echo "** Installing SBT **"
echo "********************"
echo "deb https://repo.scala-sbt.org/scalasbt/debian all main" | sudo tee /etc/apt/sources.list.d/sbt.list
echo "deb https://repo.scala-sbt.org/scalasbt/debian /" | sudo tee /etc/apt/sources.list.d/sbt_old.list
curl -sL "https://keyserver.ubuntu.com/pks/lookup?op=get&search=0x2EE0EA64E40A89B84B2DF73499E82A75642AC823" | sudo apt-key add
sudo apt-get -qq update
sudo apt-get -qq install sbt
which sbt

echo "*********************"
echo "** Installing ASDF **"
echo "*********************"
git -c advice.detachedHead=false clone --depth 1 https://github.com/asdf-vm/asdf.git $HOME/.asdf --branch v0.14.0
echo "source $HOME/.asdf/asdf.sh" >> $HOME/.bashrc
source $HOME/.asdf/asdf.sh
asdf --version
asdf plugin add java
asdf plugin add python

echo "***********************************"
echo "** Installing Python Boilerplate **"
echo "***********************************"
# Install a bunch of system-level libraries which are required for asdf to be able to install python.
# It's quite annoying that this is required; ideally asdf install python 3.x.y would just work.
# But I don't know of a way to avoid it, and it seems to come up with all the python version managers.
sudo apt-get -qq update
sudo apt-get -qq install -y gcc make zlib1g-dev libssl-dev lzma liblzma-dev libbz2-dev libsqlite3-dev libreadline-dev libffi-dev libncurses5-dev libncursesw5-dev

echo "***********************"
echo "** Cloning Elastiknn **"
echo "***********************"
git clone https://github.com/alexklibisz/elastiknn.git
cd elastiknn

# Install the asdf packages specified by elastiknn/.tool-versions
echo "*************************************"
echo "** Installing ASDF Plugin Versions **"
echo "*************************************"
asdf install

echo "*************************"
echo "** Compiling Elastiknn **"
echo "*************************"
task jvmCompile

echo "Done!"