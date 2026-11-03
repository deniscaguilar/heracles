# One ARM instance running Docker Compose: the backend (profile `all`, api + worker) and Caddy for TLS.
# No SSH and no static keys: access is by Session Manager and AWS calls use the instance role.

data "aws_ssm_parameter" "ami" {
  count = var.ami_id == "" ? 1 : 0
  name  = "/aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-arm64"
}

locals {
  ami_id = var.ami_id == "" ? data.aws_ssm_parameter.ami[0].value : var.ami_id

  # Pinned and verified by checksum in the boot script.
  compose_version = "v5.7.0"
  compose_sha256  = "15861535d14e26555736b49376c95e14bfd94285366c8446bfac7156a972e76f"
}

data "aws_iam_policy_document" "assume" {
  statement {
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["ec2.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "this" {
  name_prefix        = "${var.name}-app-"
  assume_role_policy = data.aws_iam_policy_document.assume.json
}

# Session Manager instead of SSH.
resource "aws_iam_role_policy_attachment" "ssm_core" {
  role       = aws_iam_role.this.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

data "aws_iam_policy_document" "app" {
  statement {
    sid = "Queues"
    actions = [
      "sqs:SendMessage",
      "sqs:ReceiveMessage",
      "sqs:DeleteMessage",
      "sqs:ChangeMessageVisibility",
      "sqs:GetQueueAttributes",
      "sqs:GetQueueUrl",
    ]
    resources = var.queue_arns
  }

  statement {
    sid       = "FilesBucket"
    actions   = ["s3:GetObject", "s3:PutObject", "s3:DeleteObject", "s3:AbortMultipartUpload"]
    resources = ["${var.bucket_arn}/*"]
  }

  statement {
    sid       = "FilesBucketList"
    actions   = ["s3:ListBucket"]
    resources = [var.bucket_arn]
  }

  statement {
    sid       = "FilesKey"
    actions   = ["kms:Decrypt", "kms:GenerateDataKey"]
    resources = [var.bucket_kms_key_arn]
  }

  statement {
    sid       = "Secrets"
    actions   = ["ssm:GetParametersByPath", "ssm:GetParameters", "ssm:GetParameter"]
    resources = [var.ssm_parameter_arn_pattern]
  }
}

resource "aws_iam_role_policy" "app" {
  name   = "app"
  role   = aws_iam_role.this.id
  policy = data.aws_iam_policy_document.app.json
}

resource "aws_iam_instance_profile" "this" {
  name_prefix = "${var.name}-app-"
  role        = aws_iam_role.this.name
}

resource "aws_instance" "this" {
  ami                    = local.ami_id
  instance_type          = var.instance_type
  subnet_id              = var.subnet_id
  vpc_security_group_ids = [var.security_group_id]
  iam_instance_profile   = aws_iam_instance_profile.this.name

  metadata_options {
    http_tokens                 = "required" # IMDSv2 only
    http_put_response_hop_limit = 2          # containers reach the role through the instance metadata
  }

  root_block_device {
    volume_type = "gp3"
    volume_size = var.root_volume_gb
    encrypted   = true
  }

  user_data = templatefile("${path.module}/user_data.sh.tftpl", {
    compose_version = local.compose_version
    compose_sha256  = local.compose_sha256
    ssm_path_prefix = var.ssm_path_prefix
    aws_region      = var.aws_region
    compose_yaml = templatefile("${path.module}/compose.yml.tftpl", {
      backend_image = var.backend_image
      caddy_image   = var.caddy_image
    })
    caddyfile = templatefile("${path.module}/Caddyfile.tftpl", {
      domain = var.domain
    })
    app_env = templatefile("${path.module}/app.env.tftpl", {
      database_address        = var.database_address
      database_name           = var.database_name
      database_admin_username = var.database_admin_username
      aws_region              = var.aws_region
      work_queue_url          = var.work_queue_url
      dlq_url                 = var.dlq_url
      bucket_name             = var.bucket_name
      oidc_issuer             = var.oidc_issuer
      oidc_jwk_set_uri        = var.oidc_jwk_set_uri
      oidc_audience           = var.oidc_audience
      otlp_endpoint           = var.otlp_endpoint
    })
  })
  user_data_replace_on_change = true

  tags = { Name = var.name }

  lifecycle {
    ignore_changes = [ami] # a newer AMI must not replace the host (and its Compose state) by itself
  }
}

# A stable address for DNS. Public IPv4 has an hourly charge, attached or not.
resource "aws_eip" "this" {
  instance = aws_instance.this.id
  domain   = "vpc"
  tags     = { Name = var.name }
}
