output "bucket_name" {
  value = aws_s3_bucket.files.bucket
}

output "bucket_arn" {
  value = aws_s3_bucket.files.arn
}

output "kms_key_arn" {
  value = aws_kms_key.files.arn
}
