provider "aws" {
  region = "us-west-2"  # Replace with your desired region
}

resource "aws_key_pair" "elastiknn" {
  key_name   = "elastiknn"
  public_key = file("~/.ssh/id_ed25519.pub")
}

resource "aws_security_group" "elastiknn" {
  name = "elastiknn"
  description = "Allow SSH access to Elastiknn instance"
  ingress {
    from_port = 22
    to_port = 22
    protocol = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

data "aws_ami" "elastiknn" {
  most_recent = true
  owners      = ["self"]
  filter {
    name   = "name"
    values = ["elastiknn"]
  }
}

resource "aws_instance" "elastiknn" {
  ami           = data.aws_ami.elastiknn.image_id
  instance_type = "r6i.4xlarge"
  key_name      = aws_key_pair.elastiknn.key_name
  tags = {
    Name = "elastiknn"
  }
  security_groups = [aws_security_group.elastiknn.name]
  root_block_device {
    volume_size = 42
  }
  user_data = file("provision-ubuntu-22.04.sh")
}

output "ssh_command" {
  value = "ssh ubuntu@${aws_instance.elastiknn.public_dns}"
}