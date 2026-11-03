variable "environment" {
  description = "prod, or smoke for the ephemeral environment (CA18)."
  type        = string

  validation {
    condition     = contains(["prod", "smoke"], var.environment)
    error_message = "environment must be prod or smoke."
  }
}

variable "aws_region" {
  type    = string
  default = "sa-east-1"
}

variable "availability_zones" {
  type    = list(string)
  default = ["sa-east-1a", "sa-east-1b"]
}

variable "bucket_suffix" {
  description = "Makes the files bucket name globally unique; use the AWS account id."
  type        = string
}

variable "backend_image" {
  description = "Backend image (arm64), pinned by digest."
  type        = string

  validation {
    condition     = can(regex("@sha256:[0-9a-f]{64}$", var.backend_image))
    error_message = "backend_image must be pinned by digest (name@sha256:...)."
  }
}

variable "domain" {
  description = "Public host name for TLS. Empty serves plain HTTP (smoke)."
  type        = string
  default     = ""
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

variable "alarm_email" {
  type    = string
  default = ""
}

variable "cors_allowed_origins" {
  type    = list(string)
  default = []
}

variable "ingress_cidrs" {
  type    = list(string)
  default = ["0.0.0.0/0"]
}

variable "instance_type" {
  type    = string
  default = "t4g.small"
}

variable "db_instance_class" {
  type    = string
  default = "db.t4g.micro"
}

variable "ami_id" {
  description = "Empty looks up the latest Amazon Linux 2023 arm64."
  type        = string
  default     = ""
}

variable "offline" {
  description = "Plans without AWS credentials (validation in the pipeline). Never set it for a real apply."
  type        = bool
  default     = false
}
