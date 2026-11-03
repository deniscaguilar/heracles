# Secrets in SSM Parameter Store (SecureString), never in the repository, the image or a versioned .env.
# The instance reads them at boot under /heracles/<name>/ with its role.

# Passwords of the two database roles the application creates and uses (Flyway migration of E1-S2):
# the owner of the schemas and the restricted role under RLS.
resource "random_password" "database_app" {
  length  = 32
  special = false
}

resource "random_password" "database_owner" {
  length  = 32
  special = false
}

locals {
  values = {
    "db/admin_password" = var.database_admin_password
    "db/app_password"   = random_password.database_app.result
    "db/owner_password" = random_password.database_owner.result
  }
}

resource "aws_ssm_parameter" "this" {
  for_each = nonsensitive(toset(keys(local.values)))

  name  = "/heracles/${var.name}/${each.key}"
  type  = "SecureString"
  value = local.values[each.key]
}
