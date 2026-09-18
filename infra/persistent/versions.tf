terraform {
  required_version = ">= 1.10"

  backend "s3" {
    bucket       = "wms-tfstate-721274598949"
    key          = "wareflow/persistent.tfstate"
    region       = "ap-northeast-2"
    encrypt      = true
    use_lockfile = true
  }

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
  }
}

provider "aws" {
  region = var.region

  default_tags {
    tags = {
      Project   = var.project
      ManagedBy = "terraform"
      Stack     = "persistent"
    }
  }
}
