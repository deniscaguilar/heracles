output "path_prefix" {
  value = "/heracles/${var.name}/"
}

output "parameter_arn_pattern" {
  description = "ARN pattern that covers every parameter of this environment (for the instance role)."
  value       = "arn:aws:ssm:*:*:parameter/heracles/${var.name}/*"
}
