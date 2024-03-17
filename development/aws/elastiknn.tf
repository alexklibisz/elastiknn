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

data "aws_ami" "ubuntu" {
  most_recent = true
  owners      = ["099720109477"]
  filter {
    name   = "virtualization-type"
    values = ["hvm"]
  }
  filter {
    name   = "name"
    values = ["ubuntu/images/*ubuntu-jammy-22.04-amd64-server-*"]
  }
}

resource "aws_instance" "elastiknn" {
  ami           = data.aws_ami.ubuntu.image_id
  instance_type = "r6i.4xlarge"
  key_name      = aws_key_pair.elastiknn.key_name
  tags = {
    Name = "elastiknn"
  }
  security_groups = [aws_security_group.elastiknn.name]
  root_block_device {
    volume_size = 42
  }
  user_data = <<-EOF
    #!/bin/bash
    echo "${filebase64("setup.sh")}" | base64 --decode > /home/ubuntu/setup.sh
    chmod +x /home/ubuntu/setup.sh
    chown ubuntu /home/ubuntu/setup.sh
  EOF
}

output "ssh_command" {
  value = "ssh ubuntu@${aws_instance.elastiknn.public_dns}"
}