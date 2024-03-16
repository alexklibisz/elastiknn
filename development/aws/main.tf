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

resource "aws_instance" "elastiknn" {
  ami           = "ami-08116b9957a259459"
  instance_type = "r6i.2xlarge"
  key_name      = aws_key_pair.elastiknn.key_name
  tags = {
    Name = "elastiknn"
  }
  security_groups = [aws_security_group.elastiknn.name]
}

output "ssh_command" {
  value = "ssh ubuntu@${aws_instance.elastiknn.public_dns}"
}