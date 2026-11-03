#!/usr/bin/env bash
# E1-V.2: apply the queues, storage and secrets modules to a throw-away LocalStack, check that they behave as the
# application needs, and destroy them. No AWS account, no credentials, no cost. Needs Docker, tofu, aws CLI and curl.
#
#   infra/scripts/localstack.sh          apply, check, destroy, stop LocalStack (default)
#   infra/scripts/localstack.sh keep     same, but leave the LocalStack and the resources up to look at
#   infra/scripts/localstack.sh down     stop the LocalStack container
#   HERACLES_WITH_APP=1 infra/scripts/localstack.sh
#                                        also point the backend of the compose stack (which must be up) at the queues
#                                        OpenTofu created, and follow a message through them to the DLQ
#
# LocalStack runs on port 4577 (not the 4567 of the compose stack, which only does S3) and uses the image the compose
# file pins by digest.
set -uo pipefail

here="$(cd "$(dirname "$0")/.." && pwd)"
cmd="${1:-run}"
port=4577
name=heracles-localstack-tofu
endpoint="http://localhost:$port"
image="$(sed -n 's/^ *image: \(localstack\/localstack:[^ ]*\)$/\1/p' "$here/../docker/compose.yml")"
export AWS_ACCESS_KEY_ID=test AWS_SECRET_ACCESS_KEY=test AWS_DEFAULT_REGION=sa-east-1 AWS_PAGER=""
aws_() { aws --endpoint-url "$endpoint" --cli-connect-timeout 5 --cli-read-timeout 30 "$@"; }
t() { tofu -chdir="$here/local" "$@"; }

pass=0
fail=0
ok() { echo "ok    $1"; pass=$((pass + 1)); }
bad() { echo "FAIL  $1${2:+ ($2)}"; fail=$((fail + 1)); }
is() { # is <label> <actual> <expected>
  [[ "$2" == "$3" ]] && ok "$1" || bad "$1" "got '$2', expected '$3'"
}

down() { docker rm -f "$name" > /dev/null 2>&1 || true; }
[[ "$cmd" == down ]] && { down; echo "stopped"; exit 0; }

command -v docker > /dev/null && docker info > /dev/null 2>&1 || { echo "Docker is not running"; exit 2; }
[[ -n "$image" ]] || { echo "could not read the LocalStack image from docker/compose.yml"; exit 2; }

down
echo "starting $image on port $port"
docker run -d --name "$name" -p "$port:4566" -e SERVICES=s3,sqs,sns,ssm,kms,cloudwatch,iam,sts \
  -e LOCALSTACK_HOST="localhost:$port" "$image" > /dev/null || exit 2
until curl -fs "$endpoint/_localstack/health" | grep -q '"sqs": "\(available\|running\)"'; do sleep 2; done

rm -f "$here/local/terraform.tfstate" "$here/local/terraform.tfstate.backup"
t init -input=false -no-color > /dev/null || exit 2
echo "applying"
t apply -auto-approve -input=false -no-color -var "endpoint=$endpoint" > /tmp/heracles-localstack-apply.log 2>&1 \
  || { tail -30 /tmp/heracles-localstack-apply.log; down; exit 1; }
tail -1 /tmp/heracles-localstack-apply.log | sed 's/\x1b\[[0-9;]*m//g'
work="$(t output -raw work_queue_url)"
dlq="$(t output -raw dlq_url)"
bucket="$(t output -raw files_bucket)"

echo "== queues (CA14, CA16)"
attrs="$(aws_ sqs get-queue-attributes --queue-url "$work" --attribute-names All --query Attributes --output json)"
dlq_arn="$(aws_ sqs get-queue-attributes --queue-url "$dlq" --attribute-names QueueArn --query Attributes.QueueArn --output text)"
is "the work queue redrives to the DLQ after 5 receives" \
  "$(python3 -c "import sys,json; r=json.loads(json.loads(sys.argv[1])['RedrivePolicy']); print(r['deadLetterTargetArn']==sys.argv[2], r['maxReceiveCount'])" "$attrs" "$dlq_arn")" "True 5"
is "the visibility timeout is the one the application expects (30 s)" \
  "$(python3 -c "import sys,json; print(json.loads(sys.argv[1])['VisibilityTimeout'])" "$attrs")" 30
is "the work queue refuses non-TLS access" \
  "$(python3 -c "import sys,json; print('aws:SecureTransport' in json.loads(sys.argv[1]).get('Policy',''))" "$attrs")" True
aws_ sqs send-message --queue-url "$work" --message-body '{"id":"abc"}' > /dev/null
for _ in 1 2 3 4 5 6; do
  aws_ sqs receive-message --queue-url "$work" --visibility-timeout 0 --wait-time-seconds 1 > /dev/null
done
sleep 2
is "a message received 5 times without being deleted lands in the DLQ" \
  "$(aws_ sqs get-queue-attributes --queue-url "$dlq" --attribute-names ApproximateNumberOfMessages --query Attributes.ApproximateNumberOfMessages --output text)" 1
alarms="$(aws_ cloudwatch describe-alarms --alarm-name-prefix heracles-local --query 'MetricAlarms[].[AlarmName,MetricName,Threshold]' --output text | sort)"
is "both alarms exist on the right metrics" "$(echo "$alarms" | awk '{print $2}' | tr '\n' ' ')" "ApproximateNumberOfMessagesVisible ApproximateAgeOfOldestMessage "
is "the alarms notify the SNS topic" \
  "$(aws_ cloudwatch describe-alarms --alarm-name-prefix heracles-local --query 'length(MetricAlarms[?AlarmActions[0]!=`null`])' --output text)" 2

echo "== storage (the presigned upload of ADR-0002)"
is "the bucket is versioned" "$(aws_ s3api get-bucket-versioning --bucket "$bucket" --query Status --output text)" Enabled
is "public access is fully blocked" \
  "$(aws_ s3api get-public-access-block --bucket "$bucket" --query 'PublicAccessBlockConfiguration.[BlockPublicAcls,BlockPublicPolicy,IgnorePublicAcls,RestrictPublicBuckets]' --output text | tr -s '\t' ' ')" "True True True True"
is "default encryption is KMS" \
  "$(aws_ s3api get-bucket-encryption --bucket "$bucket" --query 'ServerSideEncryptionConfiguration.Rules[0].ApplyServerSideEncryptionByDefault.SSEAlgorithm' --output text)" aws:kms
is "CORS allows the web app origin for PUT" \
  "$(aws_ s3api get-bucket-cors --bucket "$bucket" --query 'CORSRules[0].[AllowedOrigins[0],AllowedMethods[?@==`PUT`]|[0]]' --output text | tr -s '\t' ' ')" "http://localhost:5173 PUT"
url="$(aws_ s3 presign "s3://$bucket/exams/00000000-0000-0000-0000-000000000001/doc-1" --expires-in 60)"
code="$(printf 'exam bytes' | curl -s -o /dev/null -w '%{http_code}' -X PUT --data-binary @- "$url")"
is "a presigned PUT uploads without credentials" "$code" 200
is "the uploaded object is readable with the same bytes" \
  "$(aws_ s3 cp "s3://$bucket/exams/00000000-0000-0000-0000-000000000001/doc-1" - 2> /dev/null)" "exam bytes"
is "another object key does not exist" \
  "$(aws_ s3api head-object --bucket "$bucket" --key nope 2>&1 | grep -c 'Not Found\|404')" 1

echo "== secrets"
params="$(aws_ ssm get-parameters-by-path --path /heracles/local/ --recursive --with-decryption --query 'Parameters[].[Name,Type]' --output text | sort | tr '\t' ' ')"
is "the three database passwords exist as SecureString" \
  "$(echo "$params" | tr '\n' ',')" "/heracles/local/db/admin_password SecureString,/heracles/local/db/app_password SecureString,/heracles/local/db/owner_password SecureString,"
is "the admin password is the one the module was given" \
  "$(aws_ ssm get-parameter --name /heracles/local/db/admin_password --with-decryption --query Parameter.Value --output text)" local-only-not-a-secret
is "the generated passwords are 32 characters" \
  "$(aws_ ssm get-parameter --name /heracles/local/db/app_password --with-decryption --query Parameter.Value --output text | tr -d '\n' | wc -c | tr -d ' ')" 32

if [[ "${HERACLES_WITH_APP:-0}" == 1 ]]; then
  echo "== the application on the queues and bucket created by OpenTofu"
  compose=(docker compose -f "$here/../docker/compose.yml")
  psql_() { "${compose[@]}" exec -T postgres psql -U postgres -d heracles -v ON_ERROR_STOP=1 -tA "$@"; }
  # The redrive test above left a message in the DLQ; start the application step from empty queues.
  aws_ sqs purge-queue --queue-url "$work" > /dev/null 2>&1
  aws_ sqs purge-queue --queue-url "$dlq" > /dev/null 2>&1
  "${compose[@]}" -f "$here/local/compose.app.yml" up -d --wait backend > /dev/null 2>&1 \
    && ok "the backend restarted on the OpenTofu queues and bucket and is healthy" || bad "backend restart"
  id="$(python3 -c 'import uuid; print(uuid.uuid4())')"
  psql_ -c "insert into platform.task(id,user_id,type) values('$id',null,'demo.unknown'); select platform.append_outbox_message('$id','demo.unknown','task','$id','{\"taskId\":\"$id\"}'::jsonb);" > /dev/null
  echo "   following the message (publish, 5 attempts with growing back-off, DLQ), about 90 s"
  for _ in $(seq 1 40); do
    [[ "$(aws_ sqs get-queue-attributes --queue-url "$dlq" --attribute-names ApproximateNumberOfMessages --query Attributes.ApproximateNumberOfMessages --output text)" -ge 1 ]] && break
    sleep 5
  done
  is "the DLQ holds the message of this run (the application dead-lettered it after its attempts)" \
    "$(aws_ sqs receive-message --queue-url "$dlq" --visibility-timeout 0 --wait-time-seconds 1 --query 'Messages[].Body' --output text | grep -c "$id")" 1
  for _ in $(seq 1 20); do
    [[ "$(psql_ -c "select status from platform.task where id='$id'")" == FAILED ]] && break
    sleep 2
  done
  is "the task of that message is FAILED" "$(psql_ -c "select status from platform.task where id='$id'")" FAILED
  echo "   attempts the application logged for this message:"
  "${compose[@]}" logs backend 2>&1 | grep "$id" | sed 's/.*"message":"//; s/","traceId.*//' | sed 's/^/     /'
  "${compose[@]}" up -d --wait backend > /dev/null 2>&1 && ok "the backend is back on the local queues" || bad "backend restore"
fi

if [[ "$cmd" == keep ]]; then
  echo "left running: $endpoint (stop with: infra/scripts/localstack.sh down)"
else
  echo "== destroy"
  aws_ s3 rm "s3://$bucket" --recursive > /dev/null 2>&1
  # LocalStack never clears the redrive-allow attribute that the provider waits for after deleting this policy, so its
  # destroy hangs (on AWS it settles). The queue itself is deleted below; only this record is dropped from the state.
  t state rm module.queues.aws_sqs_queue_redrive_allow_policy.dlq > /dev/null
  echo "note  aws_sqs_queue_redrive_allow_policy skipped in destroy: known LocalStack limit, not a module defect"
  t destroy -auto-approve -input=false -no-color -var "endpoint=$endpoint" > /tmp/heracles-localstack-destroy.log 2>&1 \
    && ok "destroy finished" || { bad "destroy" "see /tmp/heracles-localstack-destroy.log"; tail -5 /tmp/heracles-localstack-destroy.log; }
  is "no queue is left" "$(aws_ sqs list-queues --query 'length(QueueUrls || `[]`)' --output text)" 0
  is "no bucket is left" "$(aws_ s3api list-buckets --query 'length(Buckets)' --output text)" 0
  is "no parameter is left" "$(aws_ ssm describe-parameters --query 'length(Parameters)' --output text)" 0
  down
fi

echo
echo "$pass ok, $fail failed"
((fail == 0))
