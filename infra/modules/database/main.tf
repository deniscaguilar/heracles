# Single-AZ PostgreSQL in private subnets, gp3, KMS encryption, automated backups with point-in-time recovery.
# No public access; the only ingress is the application security group (network module).

resource "aws_kms_key" "database" {
  description             = "${var.name} database"
  enable_key_rotation     = true
  deletion_window_in_days = 7
}

resource "aws_db_subnet_group" "this" {
  name       = var.name
  subnet_ids = var.subnet_ids
}

# The master password is generated here and stored in SSM by the secrets module; it never appears in a
# variable file. It does live in the state, which is why the state bucket is private and encrypted.
resource "random_password" "master" {
  length  = 32
  special = false
}

resource "aws_db_instance" "this" {
  identifier = var.name

  engine         = "postgres"
  engine_version = var.engine_version
  instance_class = var.instance_class

  db_name  = "heracles"
  username = "heracles_admin"
  password = random_password.master.result

  allocated_storage     = var.allocated_storage_gb
  max_allocated_storage = var.allocated_storage_gb * 2
  storage_type          = "gp3"
  storage_encrypted     = true
  kms_key_id            = aws_kms_key.database.arn

  multi_az               = false
  publicly_accessible    = false
  db_subnet_group_name   = aws_db_subnet_group.this.name
  vpc_security_group_ids = [var.security_group_id]

  backup_retention_period    = var.backup_retention_days
  backup_window              = "05:00-06:00" # 02:00-03:00 America/Sao_Paulo
  maintenance_window         = "sun:06:30-sun:07:30"
  copy_tags_to_snapshot      = true
  auto_minor_version_upgrade = true

  deletion_protection       = var.deletion_protection
  skip_final_snapshot       = !var.deletion_protection
  final_snapshot_identifier = var.deletion_protection ? "${var.name}-final" : null

  performance_insights_enabled = false
}
