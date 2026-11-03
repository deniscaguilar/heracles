output "public_ip" {
  description = "Point the DNS name here (prod) or open it in a browser (smoke)."
  value       = module.compute.public_ip
}

output "files_bucket" {
  value = module.storage.bucket_name
}

output "work_queue_url" {
  value = module.queues.work_queue_url
}

output "alarm_topic_arn" {
  value = module.queues.alarm_topic_arn
}

output "instance_id" {
  description = "Open a shell with: aws ssm start-session --target <id>"
  value       = module.compute.instance_id
}
