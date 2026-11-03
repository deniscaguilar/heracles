variable "name" {
  description = "Prefix for resource names, e.g. heracles-prod."
  type        = string
}

variable "subnet_id" {
  type = string
}

variable "security_group_id" {
  type = string
}

variable "instance_type" {
  description = "ARM (Graviton). Size by real use."
  type        = string
  default     = "t4g.small"
}

variable "ami_id" {
  description = "Amazon Linux 2023 arm64. Empty means the latest, looked up in the public SSM parameter."
  type        = string
  default     = ""
}

variable "root_volume_gb" {
  type    = number
  default = 20
}

variable "domain" {
  description = "Public host name for Caddy's automatic TLS. Empty serves plain HTTP on port 80 (smoke environment)."
  type        = string
  default     = ""
}

variable "backend_image" {
  description = "Backend image (arm64), pinned by digest."
  type        = string
}

variable "caddy_image" {
  type    = string
  default = "caddy:2@sha256:36c24ad91a7ff7bc4c87fe22bfa8df3c4b18da0773d73fc12ee2c52b98c0bb7e"
}

variable "aws_region" {
  type = string
}

variable "ssm_path_prefix" {
  description = "Where the secrets live, e.g. /heracles/prod/."
  type        = string
}

variable "ssm_parameter_arn_pattern" {
  type = string
}

variable "database_address" {
  type = string
}

variable "database_name" {
  type = string
}

variable "database_admin_username" {
  type = string
}

variable "queue_arns" {
  type = list(string)
}

variable "work_queue_url" {
  type = string
}

variable "dlq_url" {
  type = string
}

variable "bucket_name" {
  type = string
}

variable "bucket_arn" {
  type = string
}

variable "bucket_kms_key_arn" {
  type = string
}

variable "oidc_issuer" {
  type = string
}

variable "oidc_jwk_set_uri" {
  type = string
}

variable "oidc_audience" {
  type    = string
  default = "heracles-api"
}

variable "otlp_endpoint" {
  description = "Where OTLP goes. Empty leaves the exporter pointing nowhere useful; set it when there is a collector."
  type        = string
  default     = ""
}
