variable "name" {
  description = "Prefix for resource names, e.g. heracles-prod."
  type        = string
}

variable "subnet_ids" {
  description = "Private subnets in two zones."
  type        = list(string)
}

variable "security_group_id" {
  type = string
}

variable "engine_version" {
  description = "PostgreSQL major version; it matches the postgres image of docker/compose.yml. Confirm the minor available in sa-east-1 before the first apply."
  type        = string
  default     = "18"
}

variable "instance_class" {
  type    = string
  default = "db.t4g.micro"
}

variable "allocated_storage_gb" {
  type    = number
  default = 20
}

variable "backup_retention_days" {
  description = "Point-in-time recovery window (ADR-0002)."
  type        = number
  default     = 7
}

variable "deletion_protection" {
  description = "On for production; the smoke environment turns it off so destroy removes everything."
  type        = bool
  default     = true
}
