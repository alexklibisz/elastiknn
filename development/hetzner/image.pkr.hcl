packer {
  required_plugins {
    hcloud = {
      version = ">= 1.1.1"
      source  = "github.com/hetznercloud/hcloud"
    }
  }
}

source "hcloud" "base-amd64" {
  image         = "ubuntu-22.04"
  location      = "fsn1"
  server_type   = "ccx23"
  ssh_keys      = []
  user_data     = ""
  ssh_username  = "root"
  snapshot_name = "elastiknn-development-ubuntu-22.04"
  snapshot_labels = {
    name    = "elastiknn-development-ubuntu-22.04"
  }
}

build {
  sources = ["source.hcloud.base-amd64"]
  provisioner "shell" {
    scripts = [ "setup-ubuntu-22.04.sh" ]
    env = {
      BUILDER = "packer"
    }
  }
}