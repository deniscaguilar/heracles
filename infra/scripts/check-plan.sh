#!/usr/bin/env bash
# CA18: the plan shows the resources of RF10 and nothing outside them. Runs offline (no AWS credentials, no
# remote state): the provider skips its credential checks and the state is local and thrown away.
# Usage: infra/scripts/check-plan.sh [prod|smoke]   (default: smoke)
set -euo pipefail

env="${1:-smoke}"
here="$(cd "$(dirname "$0")/.." && pwd)"
stack="$here/stack"
work="$(mktemp -d)"
trap 'rm -rf "$work" "$stack/backend_override.tf"' EXIT

cat > "$stack/backend_override.tf" <<'HCL'
terraform {
  backend "local" {}
}
HCL

tofu -chdir="$stack" init -reconfigure -input=false -no-color > "$work/init.log" || { cat "$work/init.log"; exit 1; }
tofu -chdir="$stack" plan -input=false -lock=false -no-color -out="$work/plan.bin" \
  -var-file="envs/$env.tfvars" -var offline=true -var bucket_suffix=000000000000 \
  -var ami_id=ami-00000000000000000 \
  -var 'backend_image=ghcr.io/example/heracles@sha256:0000000000000000000000000000000000000000000000000000000000000000' \
  -var oidc_issuer=https://issuer.example -var oidc_jwk_set_uri=https://issuer.example/jwks > "$work/plan.log" \
  || { cat "$work/plan.log"; exit 1; }
tofu -chdir="$stack" show -json "$work/plan.bin" > "$work/plan.json"

python3 "$here/scripts/check_plan.py" "$work/plan.json"
