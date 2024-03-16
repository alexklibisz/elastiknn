packer {
  required_plugins {
    amazon = {
      version = ">= 1.2.8"
      source  = "github.com/hashicorp/amazon"
    }
  }
}

source "amazon-ebs" "ubuntu" {
  ami_name      = "elastiknn-ubuntu"
  instance_type = "r6i.large"
  region        = "us-west-2"
  source_ami    = "ami-08116b9957a259459"
#  source_ami_filter {
#    filters = {
#      name                = "ubuntu/images/*ubuntu-jammy-22.04-amd64-server-*"
#      root-device-type    = "ebs"
#      virtualization-type = "hvm"
#    }
#    most_recent = true
#    owners      = ["099720109477"]
#  }
  ssh_username = "ubuntu"
}

build {
  name = "elastiknn-packer"
  sources = ["source.amazon-ebs.ubuntu"]
  provisioner "shell" {
    scripts = ["provision-ubuntu-22.04.sh"]
  }
}