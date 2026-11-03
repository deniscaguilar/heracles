# The queues, storage and secrets modules applied to a LocalStack (E1-V.2): the same code that will run on AWS,
# against an emulator, at no cost. Network, database and compute are not here: LocalStack Community has no RDS,
# and EC2 there is a mock; those are proven by the smoke environment (E1-S8.4).
# Run it with infra/scripts/localstack.sh; it needs no AWS credentials.

variable "endpoint" {
  description = "LocalStack edge URL."
  type        = string
  default     = "http://localhost:4577"
}

provider "aws" {
  region     = "sa-east-1"
  access_key = "test"
  secret_key = "test"

  skip_credentials_validation = true
  skip_requesting_account_id  = true
  skip_metadata_api_check     = true
  s3_use_path_style           = true

  endpoints {
    s3         = var.endpoint
    sqs        = var.endpoint
    sns        = var.endpoint
    ssm        = var.endpoint
    kms        = var.endpoint
    cloudwatch = var.endpoint
    iam        = var.endpoint
    sts        = var.endpoint
  }
}

locals {
  name = "heracles-local"
}

module "queues" {
  source = "../modules/queues"
  name   = local.name
}

module "storage" {
  source               = "../modules/storage"
  name                 = local.name
  bucket_suffix        = "000000000000"
  cors_allowed_origins = ["http://localhost:5173"]
  force_destroy        = true
}

module "secrets" {
  source                  = "../modules/secrets"
  name                    = "local"
  database_admin_password = "local-only-not-a-secret"
}

output "work_queue_url" {
  value = module.queues.work_queue_url
}

output "dlq_url" {
  value = module.queues.dlq_url
}

output "files_bucket" {
  value = module.storage.bucket_name
}

output "alarm_topic_arn" {
  value = module.queues.alarm_topic_arn
}
