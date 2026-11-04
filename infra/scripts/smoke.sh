#!/usr/bin/env bash
# Ephemeral smoke environment (CA18): apply, smoke test and destroy, with destroy always attempted.
# THIS COSTS MONEY and touches a real AWS account. Nothing here runs by itself; see infra/README.md.
#
#   smoke.sh plan              read-only: real `tofu plan` against the account (no change, no cost)
#   smoke.sh run               apply, test, destroy (destroy runs even if the apply or the test fails)
#   smoke.sh destroy           destroy what a previous run left behind
#
# Required environment:
#   BACKEND_IMAGE              arm64 backend image pinned by digest (name@sha256:...)
#   SMOKE_ACCOUNT_ID           the 12-digit account the Dênis authorised; must match the credentials in use
# `run` and `destroy` also need --yes. Optional: AWS_REGION (default sa-east-1), SMOKE_MAX_WAIT (seconds, default 1200).
#
# State: local, in infra/.smoke/ (git-ignored) and kept until destroy succeeds, so a crashed run can still be
# cleaned up with `smoke.sh destroy`. No bucket is created for it.
set -euo pipefail

cmd="${1:-}"
confirmed=no
[[ "${2:-}" == "--yes" ]] && confirmed=yes

here="$(cd "$(dirname "$0")/.." && pwd)"
base="$here/.smoke"
work="$base/stack"
export TF_DATA_DIR="$base/data"
region="${AWS_REGION:-sa-east-1}"
max_wait="${SMOKE_MAX_WAIT:-1200}"
export AWS_DEFAULT_REGION="$region"

die() { echo "smoke: $*" >&2; exit 1; }
log() { echo "smoke: $*"; }

usage() { sed -n '2,18p' "$0" | sed 's/^# \{0,1\}//'; exit 2; }
case "$cmd" in plan | run | destroy) ;; *) usage ;; esac
if [[ "$cmd" != plan && "$confirmed" != yes ]]; then
  die "'$cmd' creates or destroys real resources: pass --yes after reading the cost note in infra/README.md"
fi

for tool in tofu aws curl python3; do command -v "$tool" > /dev/null || die "$tool not found"; done
[[ -n "${SMOKE_ACCOUNT_ID:-}" ]] || die "SMOKE_ACCOUNT_ID is not set"
[[ "${SMOKE_ACCOUNT_ID}" =~ ^[0-9]{12}$ ]] || die "SMOKE_ACCOUNT_ID must be 12 digits"
if [[ "$cmd" != destroy ]]; then
  [[ "${BACKEND_IMAGE:-}" =~ @sha256:[0-9a-f]{64}$ ]] || die "BACKEND_IMAGE must be pinned by digest (name@sha256:...)"
fi

account="$(aws sts get-caller-identity --query Account --output text)" || die "no usable AWS credentials"
[[ "$account" == "$SMOKE_ACCOUNT_ID" ]] || die "credentials are for account $account, not the authorised $SMOKE_ACCOUNT_ID"
log "account $account, region $region"

# Isolated working copy: the stack and modules plus a local backend, so nothing leaks into the committed files.
mkdir -p "$base"
rm -rf "$work" "$base/modules"
cp -R "$here/stack" "$work"
cp -R "$here/modules" "$base/modules"
rm -rf "$work/.terraform" "$work/backend_override.tf"
cat > "$work/backend_override.tf" <<HCL
terraform {
  backend "local" {
    path = "$base/terraform.tfstate"
  }
}
HCL

# Only the caller may reach the instance during the test.
my_ip="$(curl -fsS https://checkip.amazonaws.com | tr -d '[:space:]')"
[[ "$my_ip" =~ ^[0-9.]+$ ]] || die "could not find the caller's public IP"

vars=(
  -var-file=envs/smoke.tfvars
  -var "aws_region=$region"
  -var "bucket_suffix=$account"
  -var "backend_image=${BACKEND_IMAGE:-ghcr.io/none/none@sha256:0000000000000000000000000000000000000000000000000000000000000000}"
  # The smoke test does not exercise login; the keys are fetched lazily, so a placeholder issuer is enough to boot.
  -var "oidc_issuer=https://smoke.invalid"
  -var "oidc_jwk_set_uri=https://smoke.invalid/jwks"
  -var "ingress_cidrs=[\"$my_ip/32\"]"
)

t() { tofu -chdir="$work" "$@"; }
t init -input=false -no-color > /dev/null

if [[ "$cmd" == plan ]]; then
  t plan -input=false -lock=false -no-color "${vars[@]}" | tail -40
  exit 0
fi

destroy() {
  log "destroying everything"
  t destroy -auto-approve -input=false -no-color "${vars[@]}"
  remaining="$(t state list 2> /dev/null | wc -l | tr -d ' ')"
  [[ "$remaining" == 0 ]] || die "destroy left $remaining resources in the state; run 'smoke.sh destroy --yes' again"
  # Independent check by tag: nothing of the smoke environment may be left (KMS keys wait 7 days to delete and are not charged).
  left="$(aws resourcegroupstaggingapi get-resources --region "$region" \
    --tag-filters Key=project,Values=heracles Key=environment,Values=smoke \
    --query 'ResourceTagMappingList[?!contains(ResourceARN, `:kms:`)].ResourceARN' --output text)"
  [[ -z "$left" ]] || die "resources still tagged as smoke: $left"
  log "destroy confirmed: nothing left"
}

if [[ "$cmd" == destroy ]]; then
  destroy
  exit 0
fi

# run: destroy is attempted whatever happens below.
trap 'rc=$?; trap - EXIT; destroy || rc=1; exit $rc' EXIT

log "apply (RDS takes about 10 minutes)"
t apply -auto-approve -input=false -no-color "${vars[@]}"

out() { t output -raw "$1"; }
ip="$(out public_ip)"
queue_url="$(out work_queue_url)"
bucket="$(out files_bucket)"
log "instance at $ip"

fail=0
check() { # check <description> <command...>
  local d="$1"; shift
  if "$@" > /dev/null 2>&1; then log "ok   $d"; else log "FAIL $d"; fail=1; fi
}

# 1. The backend boots against the real database (Flyway ran) and answers through Caddy.
log "waiting for the backend (up to ${max_wait}s)"
deadline=$((SECONDS + max_wait))
until curl -fsS --max-time 5 "http://$ip/actuator/health" 2> /dev/null | grep -q '"status":"UP"'; do
  ((SECONDS < deadline)) || { log "FAIL backend never became healthy"; fail=1; break; }
  sleep 15
done
((fail == 0)) && log "ok   health is UP through Caddy (database reachable, migrations applied)"

# 2. Security is on: no token, no access.
check "GET /api/v1/me without token is 401" \
  bash -c "[[ \$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 http://$ip/api/v1/me) == 401 ]]"

# 3. Infrastructure facts, from the AWS API itself.
check "queue has a redrive policy to the DLQ" \
  bash -c "aws sqs get-queue-attributes --queue-url '$queue_url' --attribute-names RedrivePolicy --query Attributes.RedrivePolicy --output text | grep -q deadLetterTargetArn"
check "bucket is encrypted with KMS" \
  bash -c "aws s3api get-bucket-encryption --bucket '$bucket' --query 'ServerSideEncryptionConfiguration.Rules[0].ApplyServerSideEncryptionByDefault.SSEAlgorithm' --output text | grep -q aws:kms"
check "bucket blocks public access" \
  bash -c "[[ \$(aws s3api get-public-access-block --bucket '$bucket' --query 'PublicAccessBlockConfiguration.[BlockPublicAcls,BlockPublicPolicy,IgnorePublicAcls,RestrictPublicBuckets]' --output text | tr -s '\t' ' ') == 'True True True True' ]]"
check "database is private, encrypted and single-AZ" \
  bash -c "[[ \$(aws rds describe-db-instances --db-instance-identifier heracles-smoke --query 'DBInstances[0].[PubliclyAccessible,StorageEncrypted,MultiAZ]' --output text | tr -s '\t' ' ') == 'False True False' ]]"
check "both queue alarms exist (CA16)" \
  bash -c "[[ \$(aws cloudwatch describe-alarms --alarm-name-prefix heracles-smoke-tasks --query 'length(MetricAlarms)' --output text) == 2 ]]"
check "no SSH ingress on the application security group" \
  bash -c "! aws ec2 describe-security-group-rules --filters Name=group-id,Values=\$(aws ec2 describe-instances --instance-ids '$(out instance_id)' --query 'Reservations[0].Instances[0].SecurityGroups[0].GroupId' --output text) --query 'SecurityGroupRules[?!IsEgress && FromPort==\`22\`]' --output text | grep -q ."

((fail == 0)) || die "smoke test failed (destroy still runs)"
log "smoke test passed"
