output "address" {
  value = aws_db_instance.this.address
}

output "port" {
  value = aws_db_instance.this.port
}

output "db_name" {
  value = aws_db_instance.this.db_name
}

output "admin_username" {
  value = aws_db_instance.this.username
}

output "admin_password" {
  value     = random_password.master.result
  sensitive = true
}
