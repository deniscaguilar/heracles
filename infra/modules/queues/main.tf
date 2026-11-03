# Work queue with its DLQ (ADR-0004) and the alarms CA16 asks for. Names match the local ElasticMQ
# (heracles-tasks, heracles-tasks-dlq) with the environment as a prefix.

resource "aws_sqs_queue" "dlq" {
  name                      = "${var.name}-tasks-dlq"
  message_retention_seconds = 1209600 # 14 days: time to look at and redrive
  sqs_managed_sse_enabled   = true
}

resource "aws_sqs_queue" "work" {
  name                       = "${var.name}-tasks"
  visibility_timeout_seconds = var.visibility_timeout_seconds
  message_retention_seconds  = 345600 # 4 days
  receive_wait_time_seconds  = 20
  sqs_managed_sse_enabled    = true

  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.dlq.arn
    maxReceiveCount     = var.max_receive_count
  })
}

# Only the work queue may use this DLQ.
resource "aws_sqs_queue_redrive_allow_policy" "dlq" {
  queue_url = aws_sqs_queue.dlq.id

  redrive_allow_policy = jsonencode({
    redrivePermission = "byQueue"
    sourceQueueArns   = [aws_sqs_queue.work.arn]
  })
}

# Deny any non-TLS access to both queues.
data "aws_iam_policy_document" "tls_only" {
  for_each = {
    work = aws_sqs_queue.work.arn
    dlq  = aws_sqs_queue.dlq.arn
  }

  statement {
    sid       = "DenyInsecureTransport"
    effect    = "Deny"
    actions   = ["sqs:*"]
    resources = [each.value]

    principals {
      type        = "*"
      identifiers = ["*"]
    }

    condition {
      test     = "Bool"
      variable = "aws:SecureTransport"
      values   = ["false"]
    }
  }
}

resource "aws_sqs_queue_policy" "work" {
  queue_url = aws_sqs_queue.work.id
  policy    = data.aws_iam_policy_document.tls_only["work"].json
}

resource "aws_sqs_queue_policy" "dlq" {
  queue_url = aws_sqs_queue.dlq.id
  policy    = data.aws_iam_policy_document.tls_only["dlq"].json
}

# Not encrypted with the AWS-managed key on purpose: CloudWatch cannot publish to a topic that uses it.
# The messages carry alarm names and numbers only, never application data.
resource "aws_sns_topic" "alarms" {
  name = "${var.name}-alarms"
}

resource "aws_sns_topic_subscription" "email" {
  count     = var.alarm_email == "" ? 0 : 1
  topic_arn = aws_sns_topic.alarms.arn
  protocol  = "email"
  endpoint  = var.alarm_email
}

# The SQS metrics are emitted by AWS itself (the local ElasticMQ has none; the application also publishes
# heracles.queue.depth and heracles.outbox.oldest.age through OTLP, E1-S6.9).
resource "aws_cloudwatch_metric_alarm" "oldest_message_age" {
  alarm_name          = "${var.name}-tasks-oldest-message-age"
  alarm_description   = "The oldest message in the work queue is older than the limit: the worker is stuck or too slow."
  namespace           = "AWS/SQS"
  metric_name         = "ApproximateAgeOfOldestMessage"
  dimensions          = { QueueName = aws_sqs_queue.work.name }
  statistic           = "Maximum"
  period              = 60
  evaluation_periods  = 5
  threshold           = var.oldest_message_age_threshold_seconds
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "notBreaching"
  alarm_actions       = [aws_sns_topic.alarms.arn]
  ok_actions          = [aws_sns_topic.alarms.arn]
}

resource "aws_cloudwatch_metric_alarm" "dlq_not_empty" {
  alarm_name          = "${var.name}-tasks-dlq-not-empty"
  alarm_description   = "There is at least one message in the DLQ: something failed for good and needs a look."
  namespace           = "AWS/SQS"
  metric_name         = "ApproximateNumberOfMessagesVisible"
  dimensions          = { QueueName = aws_sqs_queue.dlq.name }
  statistic           = "Maximum"
  period              = 60
  evaluation_periods  = 1
  threshold           = 0
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "notBreaching"
  alarm_actions       = [aws_sns_topic.alarms.arn]
  ok_actions          = [aws_sns_topic.alarms.arn]
}
