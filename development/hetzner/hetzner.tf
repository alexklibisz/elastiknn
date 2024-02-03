variable "hcloud_token" {
  sensitive = true
}

variable "ssh_public_key_path" {
  type = string
  default = "~/.ssh/id_ed25519.pub"
}

variable "server_image_name_label" {
  type = string
  default = "elastiknn-development-ubuntu-22.04"
}

variable "server_location" {
  type = string
  default = "fsn1"
}

variable "server_type" {
  type = string
  default = "ccx43"
}

terraform {
  required_providers {
    hcloud = {
      source = "hetznercloud/hcloud"
    }
  }
  required_version = ">= 0.13"
}

provider "hcloud" {
  token = var.hcloud_token
}

resource "hcloud_ssh_key" "elastiknn_development" {
  name       = "elastiknn-development"
  public_key = file(var.ssh_public_key_path)
}

data "hcloud_image" "elastiknn_development" {
  with_selector = "name=${var.server_image_name_label}"
}

# Create a new server running debian
resource "hcloud_server" "elastiknn_development" {
  name        = "elastiknn-development"
  image       = data.hcloud_image.elastiknn_development.id
  server_type = var.server_type
  location    = var.server_location
  ssh_keys    = [hcloud_ssh_key.elastiknn_development.id]
  public_net {
    ipv4_enabled = true
    ipv6_enabled = true
  }
}

output "ssh_command" {
  value = "ssh root@${hcloud_server.elastiknn_development.ipv4_address}"
}