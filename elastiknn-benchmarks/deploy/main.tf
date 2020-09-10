/*
 * Sets up a fairly simple EKS cluster with autoscaling and argo workflows.
 * Cobbled together from the following resources.
 * - https://learn.hashicorp.com/terraform/kubernetes/provision-eks-cluster
 * - https://github.com/terraform-aws-modules/terraform-aws-eks/tree/master/examples/irsa
 * - https://eksworkshop.com/010_introduction/
 * - https://www.terraform.io/docs/providers/helm/index.html
 */

/*
 * Networking (VPC, subnets, availability zones).
 */
variable "region" {
    default = "us-east-1"
    description = "AWS region"
}

provider "aws" {
    version = ">=2.28.1"
    region = var.region
}

# Access list of AWS availability zones in the provider's region.
data "aws_availability_zones" "available" {}

# Access account information.
data "aws_caller_identity" "current" {}

locals {
    project_name = "elastiknn-benchmarks"
    cluster_name = "${local.project_name}-cluster"
    k8s_service_account_namespace = "kube-system"
    k8s_default_namespace = "default"
    k8s_service_account_name = "cluster-autoscaler-aws-cluster-autoscaler"
}

module "vpc" {
    source = "terraform-aws-modules/vpc/aws"
    version = "2.6.0"
    name = "${local.cluster_name}.vpc"
    cidr = "10.0.0.0/16"
    azs = data.aws_availability_zones.available.names
    private_subnets = ["10.0.1.0/24", "10.0.2.0/24", "10.0.3.0/24"]
    public_subnets = ["10.0.4.0/24", "10.0.5.0/24", "10.0.6.0/24"]

    # You can use a network address translation (NAT) gateway to enable instances in a private 
    # subnet to connect to the internet or other AWS services, but prevent the internet from 
    # initiating a connection with those instances.
    # TODO: is there a way to avoid using these? It seems like they incur a pretty large cost.
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
 * Original examples used separate security for each worker group; I combined them.
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
 * IRSA (IAM Roles for Service Accounts). Needed for autoscaling.
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
        # Low-end, on-demand nodes for running k8s infra.
        {
            name = "t2.large"
            instance_type = "t2.large"
            asg_min_size = 1
            asg_max_size = 4
            suspended_processes = ["AZRebalance"]
            addition_security_group_ids = [aws_security_group.worker_mgmt.id]
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
        # Higher-end, spot nodes for running jobs.
        {
            name = "c5.4xlarge"
            instance_type = "c5.4xlarge"
            asg_min_size = 1
            asg_max_size = 50   # Max number of nodes at any given time. Different from asg_max_capacity.
            spot_price = "0.68" # Max price set to on-demand price.
            kubelet_extra_args  = "--node-labels=node.kubernetes.io/lifecycle=spot"
            suspended_processes = ["AZRebalance"]
            addition_security_group_ids = [aws_security_group.worker_mgmt.id]
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
        }
    ]
}

# This makes it possible to use helm later in the installation.
resource "null_resource" "kubectl_config_provisioner" {
    depends_on = [module.eks]
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
    namespace = local.k8s_service_account_namespace
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
 * Node termination handler installation.
 */
resource "helm_release" "node-termination-handler" {
    name = "node-termination-handler"
    chart = "aws-node-termination-handler"
    repository = "https://aws.github.io/eks-charts"
    namespace = local.k8s_service_account_namespace
    depends_on = [null_resource.kubectl_config_provisioner]
    values = [
        templatefile("templates/node-termination-handler-values.yaml", {})
    ]
}

/*
 * Storage class for high performance storage.
 */
resource "kubernetes_storage_class" "storage-10-iops" {
    depends_on = [null_resource.kubectl_config_provisioner]
    metadata {
        name = "storage-10-iops"
    }
    storage_provisioner = "kubernetes.io/aws-ebs"
    allow_volume_expansion = false
    parameters = {
        type = "io1"
        iopsPerGB = "10"
    }
    // Seems to be needed to make sure the PVC gets created in the same zone as the node where it should be attached.
    volume_binding_mode = "WaitForFirstConsumer"
}

/*
 * Argo workflows installation.
 * Uses a helm chart but also needs to setup the cluster role and bind to the cluster role in the default namespace.
 * Cluster role based on https://github.com/argoproj/argo/blob/master/docs/workflow-rbac.md
 */
resource "kubernetes_cluster_role" "argo-workflows" {
    depends_on = [null_resource.kubectl_config_provisioner]
    metadata {
        name = "argo-workflows"
    }
    rule {
        api_groups = [""]
        resources = ["pods"]
        verbs = ["get", "watch", "patch", "update"]
    }
    rule {
        api_groups = [""]
        resources = ["pods/log"]
        verbs = ["get", "watch"]
    }
    rule {
        api_groups = [""]
        resources = ["persistentvolumeclaims", "configmaps"]
        verbs = ["create", "delete", "get", "update", "patch"]
    }
}

resource "helm_release" "argo-workflows" {
    name = "argo-workflows"
    chart = "argo"
    repository = "https://argoproj.github.io/argo-helm"
    namespace = local.k8s_service_account_namespace
    depends_on = [null_resource.kubectl_config_provisioner]
    values = [
        templatefile("templates/argo-values.yaml", {})
    ]
    // Hacky way to copy the secret needed for using artifacts.
    provisioner "local-exec" {
        command = <<EOT
        kubectl -n ${local.k8s_service_account_namespace} wait --for=condition=ready --timeout=60s pod --all
        kubectl -n ${local.k8s_service_account_namespace} get -o yaml --export secret argo-workflows-minio | \
          kubectl apply -n ${local.k8s_default_namespace} -f -
      EOT
    }
}

resource "kubernetes_role_binding" "argo-workflows" {
    depends_on = [null_resource.kubectl_config_provisioner]
    metadata {
        name = "argo-workflows"
        namespace = local.k8s_default_namespace
    }
    role_ref {
        api_group = "rbac.authorization.k8s.io"
        kind = "ClusterRole"
        name = "argo-workflows"
    }
    subject {
        kind = "ServiceAccount"
        name = "default"
        namespace = local.k8s_default_namespace
    }
}

/*
 * ECR Repositories for custom application images.
 */
resource "aws_ecr_repository" "driver" {
    name = "${local.cluster_name}.driver"
    image_tag_mutability = "MUTABLE"
}

resource "aws_ecr_repository" "elastiknn" {
    name = "${local.cluster_name}.elastiknn"
    image_tag_mutability = "MUTABLE"
}

resource "aws_ecr_repository" "datasets" {
    name = "${local.cluster_name}.datasets"
    image_tag_mutability = "MUTABLE"
}

/*
 * TODO: IAM user for containers to read/write S3 bucket.
 */


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

output "region" {
    description = "AWS region"
    value       = var.region
}

output "cluster_name" {
    description = "Kubernetes Cluster Name"
    value       = local.cluster_name
}
