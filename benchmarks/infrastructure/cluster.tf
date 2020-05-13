# Terraform configuration for an EKS cluster.
# Based on:
# - https://learn.hashicorp.com/terraform/kubernetes/provision-eks-cluster
# - https://github.com/terraform-aws-modules/terraform-aws-eks/tree/master/examples/irsa
# - https://eksworkshop.com/010_introduction/
# - https://www.terraform.io/docs/providers/helm/index.html

/*
 * Networking (VPC, subnets, availability zones)
 */
variable "region" {
    default = "us-east-1"
    description = "AWS region"
}

provider "aws" {
    version = ">=2.28.1"
    region = "us-east-1"
}

# Access list of AWS availability zones in the provider's region.
data "aws_availability_zones" "available" {}

# Access account information.
data "aws_caller_identity" "current" {}

resource "random_string" "suffix" {
    length = 8
    special = false
}

locals {
    cluster_name = lower("elastiknn-${random_string.suffix.result}")
    k8s_service_account_namespace = "kube-system"
    k8s_service_account_name = "cluster-autoscaler-aws-cluster-autoscaler"
    benchmarks_namespace_name = "benchmarks"
}

module "vpc" {
    source = "terraform-aws-modules/vpc/aws"
    version = "2.6.0"
    name = "elastiknn-vpc"
    cidr = "10.0.0.0/16"
    azs = data.aws_availability_zones.available.names
    private_subnets = ["10.0.1.0/24", "10.0.2.0/24", "10.0.3.0/24"]
    public_subnets = ["10.0.4.0/24", "10.0.5.0/24", "10.0.6.0/24"]

    # You can use a network address translation (NAT) gateway to enable instances in a private 
    # subnet to connect to the internet or other AWS services, but prevent the internet from 
    # initiating a connection with those instances. 
    enable_nat_gateway = true
    single_nat_gateway = true
    enable_dns_hostnames = true

    tags = {
        "kubernetes.io/cluster/${local.cluster_name}" = "shared"
    }

    public_subnet_tags = {
        "kubernetes.io/cluster/${local.cluster_name}" = "shared"
        "kubernetes.io/role/elb" = "1"
    }

    private_subnet_tags = {
        "kubernetes.io/cluster/${local.cluster_name}" = "shared"
        "kubernetes.io/role/internal-elb" = "1"
    }
}

/*
 * Security Groups
 * Original examples used separate security for each worker group.
 * I combined them.
 */
resource "aws_security_group" "worker_mgmt" {
    name_prefix = "worker_mgmt"
    vpc_id = module.vpc.vpc_id
    ingress {
        from_port = 22
        to_port = 22
        protocol = "tcp"
        cidr_blocks = [ "10.0.0.0/8" ]
    }
}

/*
 * IRSA (IAM Roles for Service Accounts)
 * Needed for things like autoscaling
 */
module "iam_assumable_role_admin" {
    source                        = "terraform-aws-modules/iam/aws//modules/iam-assumable-role-with-oidc"
    version                       = "~> v2.6.0"
    create_role                   = true
    role_name                     = "cluster-autoscaler"
    provider_url                  = replace(module.eks.cluster_oidc_issuer_url, "https://", "")
    role_policy_arns              = [aws_iam_policy.cluster_autoscaler.arn]
    oidc_fully_qualified_subjects = ["system:serviceaccount:${local.k8s_service_account_namespace}:${local.k8s_service_account_name}"]
}

resource "aws_iam_policy" "cluster_autoscaler" {
    name_prefix = "cluster-autoscaler"
    description = "EKS cluster-autoscaler policy for cluster ${module.eks.cluster_id}"
    policy = data.aws_iam_policy_document.cluster_autoscaler.json
}

data "aws_iam_policy_document" "cluster_autoscaler" {
    statement {
        sid    = "clusterAutoscalerAll"
        effect = "Allow"
        actions = [
            "autoscaling:DescribeAutoScalingGroups",
            "autoscaling:DescribeAutoScalingInstances",
            "autoscaling:DescribeLaunchConfigurations",
            "autoscaling:DescribeTags",
            "ec2:DescribeLaunchTemplateVersions",
        ]
        resources = ["*"]
    }
    statement {
        sid    = "clusterAutoscalerOwn"
        effect = "Allow"
        actions = [
            "autoscaling:SetDesiredCapacity",
            "autoscaling:TerminateInstanceInAutoScalingGroup",
            "autoscaling:UpdateAutoScalingGroup"
        ]
    resources = ["*"]
    condition {
        test     = "StringEquals"
        variable = "autoscaling:ResourceTag/kubernetes.io/cluster/${module.eks.cluster_id}"
        values   = ["owned"]
    }
    condition {
        test     = "StringEquals"
        variable = "autoscaling:ResourceTag/k8s.io/cluster-autoscaler/enabled"
        values   = ["true"]
    }
  }
}

/*
 * EKS Cluster setup.
 */
module "eks" {
    source = "terraform-aws-modules/eks/aws"
    cluster_name = local.cluster_name
    subnets = module.vpc.private_subnets
    vpc_id = module.vpc.vpc_id
    enable_irsa = true
    worker_groups = [
        {
            name = "default"
            instance_type = "t2.small"
            asg_desired_capacity = 1
            asg_max_size = 10 # Not to be confused with asg_max_capacity. Not sure of the difference.
            additional_security_group_ids = [aws_security_group.worker_mgmt.id]
            tags = [
                {
                    "key"                 = "k8s.io/cluster-autoscaler/enabled"
                    "propagate_at_launch" = "false"
                    "value"               = "true"
                },
                {
                    "key"                 = "k8s.io/cluster-autoscaler/${local.cluster_name}"
                    "propagate_at_launch" = "false"
                    "value"               = "true"
                }
            ]
        },
        # {
        #     name = "high-performance"
        #     instance_type = "c5.4xlarge"
        #     asg_min_size = 0
        #     asg_max_size = 50
        #     addition_security_group_ids = [aws_security_group.worker_mgmt.id]
        #     tags = [
        #         {
        #             "key"                 = "k8s.io/cluster-autoscaler/enabled"
        #             "propagate_at_launch" = "false"
        #             "value"               = "true"
        #         },
        #         {
        #             "key"                 = "k8s.io/cluster-autoscaler/${local.cluster_name}"
        #             "propagate_at_launch" = "false"
        #             "value"               = "true"
        #         }
        #     ]
        # }
    ]
    tags = {
        Environment = "elastiknn"
        # Left out some tags from the original example.
    }
}

# This makes it possible to use helm later in the installation.
resource "null_resource" "kubectl_config_provisioner" {
    triggers = {
        kubectl_config = module.eks.kubeconfig
    }
    provisioner "local-exec" {
        command = <<EOT
        aws eks --region ${var.region} wait cluster-active --name ${local.cluster_name}
        aws eks --region ${var.region} update-kubeconfig --name ${local.cluster_name}
        EOT
    }
}

/*
 * Cluster autoscaler installation.
 */
resource "helm_release" "cluster-autoscaler" {
    name = "cluster-autoscaler"
    chart = "cluster-autoscaler"
    repository = "https://kubernetes-charts.storage.googleapis.com" 
    namespace = "kube-system"
    depends_on = [null_resource.kubectl_config_provisioner]
    values = [
        templatefile("templates/autoscaler-values.yaml", {
            region = var.region,
            accountId = data.aws_caller_identity.current.account_id,
            clusterName = local.cluster_name
        })
    ]
}

/*
 * Argo workflows installation.
 */

# Based on https://github.com/argoproj/argo/blob/master/docs/workflow-rbac.md
resource "kubernetes_cluster_role" "argo-workflows" {
    metadata {
        name = "argo-workflows"
    }
    rule {
        api_groups = [""]
        resources = ["pods"]
        verbs = ["get", "watch", "patch"]
    }
    rule {
        api_groups = [""]
        resources = ["pods/log"]
        verbs = ["get", "watch"]
    }
}

resource "helm_release" "argo-workflows" {
    name = "argo-workflows"
    chart = "argo"
    repository = "https://argoproj.github.io/argo-helm"
    namespace = "kube-system"
    depends_on = [null_resource.kubectl_config_provisioner, kubernetes_namespace.benchmarks]
}


/*
 * Namespaces.
 */
resource "kubernetes_namespace" "benchmarks" {
    metadata {
        name = local.benchmarks_namespace_name
    }
}

resource "kubernetes_role_binding" "argo-workflows" {
    depends_on = [kubernetes_namespace.benchmarks]
    metadata {
        name = "argo-workflows"
        namespace = local.benchmarks_namespace_name
    }
    role_ref {
        api_group = "rbac.authorization.k8s.io"
        kind = "ClusterRole"
        name = "argo-workflows"
    }
    subject {
        kind = "ServiceAccount"
        name = "default"
        namespace = local.benchmarks_namespace_name
    }
}


/*
 * ECR Repositories for custom application images.
 */
resource "aws_ecr_repository" "benchmark-driver" {
    name = "benchmark-driver"
    image_tag_mutability = "MUTABLE"
}

/*
 * S3 Buckets for data and results.
 */
resource "aws_s3_bucket" "dummy" {
    bucket = "${local.cluster_name}-dummy"
    acl = "private"
}

resource "aws_s3_bucket" "benchmark-results" {
    bucket = "${local.cluster_name}-benchmark-results"
    acl = "private"
}

/*
 * Outputs from setup.
 */
data "aws_eks_cluster" "cluster" {
    name = module.eks.cluster_id
}

data "aws_eks_cluster_auth" "cluster" {
    name = module.eks.cluster_id
}

provider "kubernetes" {
    load_config_file = "false"
    host = data.aws_eks_cluster.cluster.endpoint
    token = data.aws_eks_cluster_auth.cluster.token
    cluster_ca_certificate = base64decode(data.aws_eks_cluster.cluster.certificate_authority.0.data)
}

output "cluster_endpoint" {
    description = "Endpoint for EKS control plane."
    value       = module.eks.cluster_endpoint
}

output "cluster_security_group_id" {
    description = "Security group ids attached to the cluster control plane."
    value       = module.eks.cluster_security_group_id
}

output "kubectl_config" {
    description = "kubectl config as generated by the module."
    value       = module.eks.kubeconfig
}

# output "config_map_aws_auth" {
#   description = "A kubernetes configuration to authenticate to this EKS cluster."
#   value       = module.eks.config_map_aws_auth
# }

output "region" {
    description = "AWS region"
    value       = var.region
}

output "cluster_name" {
    description = "Kubernetes Cluster Name"
    value       = local.cluster_name
}

output "bucket_name" {
    description = "Name of dummy bucket"
    value = aws_s3_bucket.dummy.bucket
}