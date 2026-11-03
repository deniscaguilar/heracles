variable "name" {
  description = "Prefix for resource names, e.g. heracles-prod."
  type        = string
}

variable "cidr_block" {
  type    = string
  default = "10.20.0.0/16"
}

variable "availability_zones" {
  description = "Two zones: the RDS subnet group needs subnets in at least two."
  type        = list(string)

  validation {
    condition     = length(var.availability_zones) == 2
    error_message = "Exactly two availability zones are expected."
  }
}

variable "ingress_cidrs" {
  description = "Who may reach Caddy on 80 and 443. Open by default: the app is public; restrict it for the smoke environment."
  type        = list(string)
  default     = ["0.0.0.0/0"]
}
