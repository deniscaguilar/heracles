#!/usr/bin/env bash
# Local end-to-end walkthrough of the foundation (Stage 1) against the LIVE compose stack.
# It exercises what the stack really exposes: the API with real tokens from the fake OIDC server, the database, the
# queues (ElasticMQ) and the telemetry (Grafana). Each line names the acceptance criterion it gives evidence for.
#
#   docker compose -f docker/compose.yml up -d --build --wait
#   docker/validate.sh
#
# Repeatable: every run uses new users and ids. The asynchronous part waits for the retries with growing back-off
# (about 80 seconds) before the message reaches the DLQ.
# Not covered here, because the live stack has no entry point for it (no module registers a handler yet; the
# integration tests cover it): idempotent consumer effect (CA13) and file upload to S3 (CA17, S3FileStorageIT).
set -uo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
compose=(docker compose -f "$here/compose.yml")
api="http://localhost:${HERACLES_API_PORT:-8080}"
issuer="http://localhost:${HERACLES_OIDC_PORT:-8082}/default"
sqs="http://localhost:${HERACLES_SQS_PORT:-9324}/000000000000"
grafana="http://localhost:${HERACLES_GRAFANA_PORT:-3001}/api/datasources/proxy/uid"
run="$(date +%s)"
since="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

pass=0
fail=0
ok() { echo "ok    $1"; pass=$((pass + 1)); }
bad() { echo "FAIL  $1${2:+ ($2)}"; fail=$((fail + 1)); }
check() { # check <label> <command...>
  local label="$1"; shift
  if "$@" > /dev/null 2>&1; then ok "$label"; else bad "$label"; fi
}
psql_() { "${compose[@]}" exec -T postgres psql -U postgres -d heracles -v ON_ERROR_STOP=1 -tA "$@"; }
json() { python3 -c "import sys,json; d=json.load(sys.stdin); print($1)"; }

# A token for <sub> through the real authorization-code + PKCE flow of the fake server.
token() {
  local sub="$1" redirect=http://localhost/cb verifier challenge url loc code
  verifier="$(openssl rand -base64 48 | tr -d '=+/\n' | cut -c1-64)"
  challenge="$(printf %s "$verifier" | openssl dgst -sha256 -binary | openssl base64 -A | tr '+/' '-_' | tr -d '=')"
  url="$issuer/authorize?client_id=heracles-api&response_type=code&redirect_uri=$redirect&scope=openid&state=s&code_challenge=$challenge&code_challenge_method=S256"
  loc="$(curl -s -o /dev/null -D - -X POST "$url" --data-urlencode "username=$sub" --data-urlencode "claims=" | tr -d '\r' | awk 'tolower($1)=="location:"{print $2}')"
  code="$(printf %s "$loc" | sed -n 's/.*[?&]code=\([^&]*\).*/\1/p')"
  curl -s -X POST "$issuer/token" -d grant_type=authorization_code -d "code=$code" -d "redirect_uri=$redirect" \
    -d client_id=heracles-api -d "code_verifier=$verifier" | json 'd["access_token"]'
}
get() { curl -s -H "Authorization: Bearer $1" "$api$2"; }
status() { curl -s -o /dev/null -w '%{http_code}' -H "Authorization: Bearer $1" "$api$2"; }
queue_count() { curl -s "$sqs/$1?Action=GetQueueAttributes&AttributeName.1=ApproximateNumberOfMessages" | sed -n 's:.*<Value>\([0-9]*\)</Value>.*:\1:p' | head -1; }

echo "== CA3: the stack is healthy"
if "$here/check.sh" > /dev/null 2>&1; then ok "CA3 every service answers (docker/check.sh)"; else bad "CA3 docker/check.sh"; fi

echo "== CA4, CA5, CA10: authentication, identity, errors"
code="$(curl -s -o /dev/null -w '%{http_code}' "$api/api/v1/me")"
[[ "$code" == 401 ]] && ok "CA4 no token is 401" || bad "CA4 no token" "got $code"
body="$(curl -s "$api/api/v1/me")"
[[ "$(echo "$body" | json '"code" in d and "traceId" in d')" == True ]] && ok "CA4 the 401 has the standard body (code, traceId)" || bad "CA4 401 body"
code="$(status not.a.jwt /api/v1/me)"
[[ "$code" == 401 ]] && ok "CA4 a garbage token is 401" || bad "CA4 garbage token" "got $code"

alice="val-alice-$run"
bob="val-bob-$run"
ta="$(token "$alice")"
tb="$(token "$bob")"
[[ -n "$ta" && -n "$tb" ]] && ok "tokens issued by the fake OIDC server" || { bad "tokens"; exit 1; }
me="$(get "$ta" /api/v1/me)"
[[ "$(echo "$me" | json 'sorted(d.keys()) == ["id","role"] and d["role"] == "USER"')" == True ]] && ok "CA5 a new subject is created as USER and the answer has only id and role" || bad "CA5" "$me"
alice_id="$(echo "$me" | json 'd["id"]')"
[[ "$(get "$ta" /api/v1/me | json 'd["id"]')" == "$alice_id" ]] && ok "CA5 the second call returns the same user" || bad "CA5 second call"
bob_id="$(get "$tb" /api/v1/me | json 'd["id"]')"

err="$(get "$ta" /api/v1/tasks/not-a-uuid)"
trace="$(echo "$err" | json 'd["traceId"]')"
[[ "$(echo "$err" | json 'd["code"]')" == invalid_parameter ]] && ok "CA10 a bad parameter gives a stable code" || bad "CA10 code" "$err"
sleep 8
tempo="$(curl -sf -u admin:admin "$grafana/tempo/api/traces/$trace")"
if [[ "$tempo" == *heracles-backend* ]]; then ok "CA10 the traceId of the error is a real trace in Tempo"; else bad "CA10 trace not found in Tempo" "$trace"; fi

echo "== CA6, CA8: roles, isolation, audit"
[[ "$(status "$ta" /api/v1/admin/audit-events)" == 403 ]] && ok "CA6 a USER gets 403 on /admin" || bad "CA6 admin as USER"
psql_ -c "update platform.app_user set role='ADMIN' where sub='$alice'" > /dev/null
[[ "$(status "$ta" /api/v1/admin/audit-events)" == 200 ]] && ok "CA6 after promotion in the database the same token reaches /admin (the role is not in the token)" || bad "CA6 admin after promotion"
events="$(get "$ta" '/api/v1/admin/audit-events?limit=5')"
[[ "$(echo "$events" | json 'len(d["items"]) > 0 and all("action" in e and "result" in e and "occurredAt" in e for e in d["items"])')" == True ]] && ok "CA8 the audit trail lists who, when, what and result" || bad "CA8 audit list"
out="$(psql_ -c "update platform.audit_event set result='SUCCESS'" 2>&1)"
[[ "$out" == *"append-only"* ]] && ok "CA8 the database refuses UPDATE on the audit trail, even as superuser" || bad "CA8 UPDATE was not refused" "$out"
out="$(psql_ -c "delete from platform.audit_event" 2>&1)"
[[ "$out" == *"append-only"* ]] && ok "CA8 the database refuses DELETE on the audit trail" || bad "CA8 DELETE was not refused" "$out"

echo "== CA9: sentinel values do not reach logs or errors"
sentinel="cpf-123-456-789-09-segredo-de-saude-$run"
resp="$(curl -s -H "Authorization: Bearer $tb" -H "X-Sentinel: $sentinel" "$api/api/v1/tasks/$sentinel?q=$sentinel")"
sleep 3
if [[ "$resp" == *"$sentinel"* ]]; then bad "CA9 the error body echoes the value"; else ok "CA9 the error body does not echo the value"; fi
# The trace of that very request must exist (positive control) and must not carry the value.
sent_trace="$(echo "$resp" | json 'd["traceId"]')"
sleep 8
sent_spans="$(curl -sf -u admin:admin "$grafana/tempo/api/traces/$sent_trace")"
if [[ "$sent_spans" != *heracles-backend* ]]; then bad "CA9 the trace of the sentinel request was not found (the check would prove nothing)"
elif [[ "$sent_spans" == *"$sentinel"* ]]; then bad "CA9 the value reached the trace"
else ok "CA9 the value is not in the trace of the request (${#sent_spans} bytes of trace read)"; fi

echo "== CA12, CA14, CA15, CA16: outbox, queue, retries, DLQ, status, metrics"
task="$(python3 -c 'import uuid; print(uuid.uuid4())')"
ghost="$(python3 -c 'import uuid; print(uuid.uuid4())')"
before_outbox="$(psql_ -c "select count(*) from platform.outbox_message")"
psql_ -c "begin; select platform.append_outbox_message('$ghost','demo.unknown','task','$ghost','{\"userId\":\"$alice_id\"}'::jsonb); rollback;" > /dev/null
[[ "$(psql_ -c "select count(*) from platform.outbox_message")" == "$before_outbox" ]] && ok "CA12 a rolled-back transaction leaves no outbox message" || bad "CA12 rollback"
psql_ -c "insert into platform.task(id,user_id,type) values('$task','$bob_id','demo.unknown'); select platform.append_outbox_message('$task','demo.unknown','task','$task','{\"userId\":\"$bob_id\",\"taskId\":\"$task\"}'::jsonb);" > /dev/null
for _ in $(seq 1 20); do
  [[ "$(psql_ -c "select published_at is not null from platform.outbox_message where id='$task'")" == t ]] && break
  sleep 1
done
[[ "$(psql_ -c "select published_at is not null from platform.outbox_message where id='$task'")" == t ]] && ok "CA12 a committed message was published to the queue" || bad "CA12 outbox not published"

st="$(get "$tb" /api/v1/tasks/$task)"
[[ "$(echo "$st" | json 'sorted(d.keys()) == ["id","resultRef","status"]')" == True ]] && ok "CA15 the owner sees only id, status and resultRef" || bad "CA15 shape" "$st"
[[ "$(status "$ta" /api/v1/tasks/$task)" == 404 ]] && ok "CA6 another user gets 404 for a task that is not theirs (same as unknown)" || bad "CA6 task isolation"

echo "   waiting for the retries with growing back-off until the DLQ (about 80 s)"
for _ in $(seq 1 60); do
  [[ "$(get "$tb" /api/v1/tasks/$task | json 'd["status"]')" == FAILED ]] && break
  sleep 3
done
[[ "$(get "$tb" /api/v1/tasks/$task | json 'd["status"]')" == FAILED ]] && ok "CA14 after the attempts the task is FAILED" || bad "CA14 task never failed"
logs="$("${compose[@]}" logs backend 2>&1)"
attempts="$(grep -c "$task.*failed on attempt" <<< "$logs")"
[[ "$attempts" -ge 4 ]] && ok "CA14 the message was retried before giving up ($attempts failed attempts in the log)" || bad "CA14 retries" "$attempts"
[[ "$(grep -c "$task.*dead-lettered after 5 attempts" <<< "$logs")" -ge 1 ]] && ok "CA14 the message was dead-lettered after 5 attempts" || bad "CA14 dead-letter log"
[[ "$(queue_count heracles-tasks-dlq)" -ge 1 ]] && ok "CA14 the DLQ holds the message" || bad "CA14 DLQ empty"
sleep 16
depth="$(curl -s -u admin:admin "$grafana/prometheus/api/v1/query?query=heracles_queue_depth%7Bqueue%3D%22dlq%22%7D" | json 'd["data"]["result"][0]["value"][1]')"
[[ "${depth%.*}" -ge 1 ]] && ok "CA16 the DLQ depth metric is emitted (heracles_queue_depth{queue=\"dlq\"} = $depth)" || bad "CA16 metric" "$depth"
for metric in heracles_outbox_oldest_age_seconds heracles_task_oldest_age_seconds; do
  res="$(curl -s -u admin:admin "$grafana/prometheus/api/v1/query?query=$metric")"
  [[ "$res" == *'"result":[{'* ]] && ok "CA16 the metric $metric is emitted" || bad "CA16 metric $metric"
done

code="$(curl -s -o /dev/null -w '%{http_code}' -X POST -H "Authorization: Bearer $ta" "$api/api/v1/admin/dead-letters/$task/reprocess")"
[[ "$code" == 202 ]] && ok "CA14 the admin reprocess endpoint accepted the dead message (202)" || bad "CA14 reprocess" "got $code"
code="$(curl -s -o /dev/null -w '%{http_code}' -X POST -H "Authorization: Bearer $tb" "$api/api/v1/admin/dead-letters/$task/reprocess")"
[[ "$code" == 403 ]] && ok "CA6 a USER cannot reprocess (403)" || bad "CA6 reprocess as USER" "got $code"

echo "== CA9 again: the whole run's logs"
logs="$("${compose[@]}" logs backend --since "$since" 2>&1)"
if [[ "$logs" != *"$task"* ]]; then bad "CA9 the log of this run could not be read (it should name the task id)"
elif [[ "$logs" == *"$sentinel"* ]]; then bad "CA9 the value reached the logs"
else ok "CA9 the value is not in the application logs (${#logs} bytes read, and the log does name the task id)"; fi

echo
echo "$pass ok, $fail failed"
echo "Look at it yourself: Grafana http://localhost:${HERACLES_GRAFANA_PORT:-3001} (admin/admin), queues http://localhost:${HERACLES_SQS_UI_PORT:-9325}"
((fail == 0))
