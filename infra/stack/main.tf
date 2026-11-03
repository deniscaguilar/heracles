provider "aws" {
  region = var.aws_region

  # Only for `plan` without credentials (offline = true); a real run validates everything.
  skip_credentials_validation = var.offline
  skip_requesting_account_id  = var.offline
  skip_metadata_api_check     = var.offline
  access_key                  = var.offline ? "offline" : null
  secret_key                  = var.offline ? "offline" : null

  default_tags {
    tags = {
      project     = "heracles"
      environment = var.environment
      managed_by  = "opentofu"
    }
  }
}

locals {
  name = "heracles-${var.environment}"
  prod = var.environment == "prod"
}

module "network" {
  source             = "../modules/network"
  name               = local.name
  availability_zones = var.availability_zones
  ingress_cidrs      = var.ingress_cidrs
}

module "database" {
  source                = "../modules/database"
  name                  = local.name
  subnet_ids            = module.network.private_subnet_ids
  security_group_id     = module.network.database_security_group_id
  instance_class        = var.db_instance_class
  deletion_protection   = local.prod
  backup_retention_days = local.prod ? 7 : 1
}

module "storage" {
  source               = "../modules/storage"
  name                 = local.name
  bucket_suffix        = var.bucket_suffix
  cors_allowed_origins = var.cors_allowed_origins
  force_destroy        = !local.prod
}

module "queues" {
  source      = "../modules/queues"
  name        = local.name
  alarm_email = var.alarm_email
}

module "secrets" {
  source                  = "../modules/secrets"
  name                    = var.environment
  database_admin_password = module.database.admin_password
}

module "compute" {
  source            = "../modules/compute"
  name              = local.name
  subnet_id         = module.network.public_subnet_id
  security_group_id = module.network.app_security_group_id
  instance_type     = var.instance_type
  ami_id            = var.ami_id
  domain            = var.domain
  backend_image     = var.backend_image
  aws_region        = var.aws_region

  ssm_path_prefix           = module.secrets.path_prefix
  ssm_parameter_arn_pattern = module.secrets.parameter_arn_pattern

  database_address        = module.database.address
  database_name           = module.database.db_name
  database_admin_username = module.database.admin_username

  queue_arns     = [module.queues.work_queue_arn, module.queues.dlq_arn]
  work_queue_url = module.queues.work_queue_url
  dlq_url        = module.queues.dlq_url

  bucket_name        = module.storage.bucket_name
  bucket_arn         = module.storage.bucket_arn
  bucket_kms_key_arn = module.storage.kms_key_arn

  oidc_issuer      = var.oidc_issuer
  oidc_jwk_set_uri = var.oidc_jwk_set_uri
  oidc_audience    = var.oidc_audience
}
