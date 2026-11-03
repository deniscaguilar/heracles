output "work_queue_url" {
  value = aws_sqs_queue.work.url
}

output "work_queue_arn" {
  value = aws_sqs_queue.work.arn
}

output "dlq_url" {
  value = aws_sqs_queue.dlq.url
}

output "dlq_arn" {
  value = aws_sqs_queue.dlq.arn
}

output "alarm_topic_arn" {
  value = aws_sns_topic.alarms.arn
}
