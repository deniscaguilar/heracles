variable "name" {
  description = "Prefix for resource names, e.g. heracles-prod."
  type        = string
}

variable "max_receive_count" {
  description = "Deliveries before a message goes to the DLQ."
  type        = number
  default     = 5
}

variable "visibility_timeout_seconds" {
  type    = number
  default = 30
}

variable "oldest_message_age_threshold_seconds" {
  description = "The alarm fires when the oldest message in the work queue is older than this."
  type        = number
  default     = 300
}

variable "alarm_email" {
  description = "Where alarms are sent. Empty means the topic exists but nobody is subscribed yet."
  type        = string
  default     = ""
}
