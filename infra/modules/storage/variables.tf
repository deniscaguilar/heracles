variable "name" {
  description = "Prefix for resource names, e.g. heracles-prod."
  type        = string
}

variable "bucket_suffix" {
  description = "Makes the bucket name globally unique (e.g. the AWS account id)."
  type        = string
}

variable "cors_allowed_origins" {
  description = "Origins that may PUT/GET with a presigned URL (the web app). The mobile app is not a browser and needs no CORS."
  type        = list(string)
  default     = []
}

variable "force_destroy" {
  description = "Lets destroy empty a bucket that still has objects. Only the smoke environment sets it."
  type        = bool
  default     = false
}
