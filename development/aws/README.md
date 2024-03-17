# AWS Development Setup

This directory contains [Packer](https://www.packer.io/) and [Terraform](https://www.terraform.io/) files for creating a development instance in AWS.

## Assumptions

* You already have an AWS account and a way to authenticate from the command line (e.g., an IAM user with a secret access key).
* You have Packer installed. If not, [see these docs.](https://developer.hashicorp.com/packer/tutorials/docker-get-started/get-started-install-cli)
* You have Terraform installed. If not, [see these docs.](https://developer.hashicorp.com/terraform/tutorials/aws-get-started/install-cli)
* You have an SSH key at `~/.ssh/id_ed25519.pub`. If not, update the path in elastiknn.tf.

## Cost

We're using the r6i.4xlarge instance, which is a rather large and expensive instance (about $25 / day).
Make sure to run `terraform destroy` when you're done so you don't encounter a surprise bill.
If you want to use a smaller instance, modify the elastiknn.tf file. 

Storing the image 

## Usage

1. Authenticate with AWS, e.g., export your client ID and secret access key:
    ```shell
    export AWS_ACCESS_KEY_ID="..."
    export AWS_SECRET_ACCESS_KEY="..."
    ```
2. Build the AMI using Packer:
    ```shell
    packer build --force elastiknn.pkr.hcl
    ...
    Build 'elastiknn.amazon-ebs.ubuntu' finished after 8 minutes 17 seconds.
    
    ==> Wait completed after 8 minutes 17 seconds
    
    ==> Builds finished. The artifacts of successful builds are:
    --> elastiknn.amazon-ebs.ubuntu: AMIs were created:
    us-west-2: ami-025178966b4a7d9d8
    ```
3. Initialize Terraform:
    ```shell
    terraform init
    ```
4. Create the EC2 Instance using Terraform:
    ```shell
    terraform apply
    ...
    aws_key_pair.elastiknn: Creating...
    aws_security_group.elastiknn: Creating...
    aws_key_pair.elastiknn: Creation complete after 0s [id=elastiknn]
    aws_security_group.elastiknn: Creation complete after 2s [id=sg-017de4b9f5575ccfa]
    aws_instance.elastiknn: Creating...
    aws_instance.elastiknn: Still creating... [10s elapsed]
    aws_instance.elastiknn: Creation complete after 13s [id=i-01a6c42c33782028f]
    
    Apply complete! Resources: 3 added, 0 changed, 0 destroyed.
    
    Outputs:
    
    ssh_command = "ssh ubuntu@ec2-12-345-678-901.us-west-2.compute.amazonaws.com"
    ```
5. Copy and run the `ssh_command` output from the previous step to ssh into the instance.
    ```shell
    ssh ubuntu@ec2-12-345-678-901.us-west-2.compute.amazonaws.com
    ```
6. The elastiknn repo is already cloned at ~/elastiknn and all of the development software has been installed. You should be able to start developing, running benchmarks, etc.
7. When you're done, destroy the EC2 instance.
    ```shell
    terraform destroy
    ```
