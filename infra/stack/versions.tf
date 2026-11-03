terraform {
  required_version = ">= 1.10.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "6.71.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "3.8.1"
    }
  }

  # Remote state in S3 with native locking. Left empty on purpose: the bucket is not created until the
  # Dênis authorises the AWS account (E1-S8.1). Bucket, key and region come from a backend config file:
  #   tofu init -backend-config=envs/prod.backend.hcl
  # Validation and offline plans use `tofu init -backend=false`.
  backend "s3" {}
}
