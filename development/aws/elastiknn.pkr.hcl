packer {
  required_plugins {
    amazon = {
      version = ">= 1.2.8"
      source  = "github.com/hashicorp/amazon"
    }
  }
}

source "amazon-ebs" "ubuntu" {
  ami_name      = "elastiknn"
  instance_type = "r6i.large"
  region        = "us-west-2"
  # Latest ubuntu 22.04.
  source_ami_filter {
    filters = {
      name                = "ubuntu/images/*ubuntu-jammy-22.04-amd64-server-*"
      root-device-type    = "ebs"
      virtualization-type = "hvm"
    }
    most_recent = true
    owners      = ["099720109477"]
  }
  ssh_username = "ubuntu"
  force_deregister = true
  force_delete_snapshot = true
  launch_block_device_mappings {
    device_name = "/dev/sda1"
    volume_size = 8
    volume_type = "gp2"
    delete_on_termination = true
  }
}

build {
  name = "elastiknn"
  sources = ["source.amazon-ebs.ubuntu"]
  provisioner "shell" {
    scripts = ["provision-ubuntu-22.04.sh"]
  }
}