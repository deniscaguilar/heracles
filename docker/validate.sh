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
# Stage 2 (consent, anamnesis, screening, export, erasure) runs at the end, on the same stack, with its own CA labels.
# Not covered here (the integration tests cover it): idempotent consumer effect and file upload to S3 (S3FileStorageIT).
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

if [[ -z "${STAGE2_ONLY:-}" ]]; then  # STAGE2_ONLY=1 skips the Stage 1 walkthrough (it waits about 80 s for the DLQ)
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


fi

# ---------------------------------------------------------------------------------------------------------------------
# Stage 2: consent, anamnesis, screening, data export and account erasure (E2-S9.2). Same stack, same users and tokens.
# alice is ADMIN here (promoted above); carol and dave are new people. Texts and values carry run-unique sentinels.
# ---------------------------------------------------------------------------------------------------------------------
send() { # send <token> <METHOD> <path> [json]  -> body, then the status on the last line
  local t="$1" m="$2" path="$3" data="${4:-}"
  if [[ -n "$data" ]]; then
    curl -s -w '\n%{http_code}' -X "$m" -H "Authorization: Bearer $t" -H 'Content-Type: application/json' -d "$data" "$api$path"
  else
    curl -s -w '\n%{http_code}' -X "$m" -H "Authorization: Bearer $t" "$api$path"
  fi
}
req() { # req <token> <METHOD> <path> [json]  -> sets RCODE and RBODY (no nested quoting at the call sites)
  local r; r="$(send "$@")"; RCODE="$(tail -n 1 <<< "$r")"; RBODY="$(sed '$d' <<< "$r")"
}
body_of() { sed '$d' <<< "$1"; }
code_of() { tail -n 1 <<< "$1"; }

echo "== Stage 2 setup: an admin publishes the texts"
admin="val-admin-$run"
tadmin="$(token "$admin")"
get "$tadmin" /api/v1/me > /dev/null
psql_ -c "update platform.app_user set role='ADMIN' where sub='$admin'" > /dev/null
publish_text() { # publish_text <type> <markdown>  -> prints the version id
  local d id
  d="$(send "$tadmin" POST "/api/v1/admin/policies/$1/draft" "{\"title\":\"Validation $1\",\"markdown\":\"$2\"}")"
  id="$(body_of "$d" | json 'd["id"]')"
  send "$tadmin" POST "/api/v1/admin/policy-drafts/$id/publish" > /dev/null
  echo "$id"
}
for type in TERMS_OF_USE PRIVACY_POLICY HEALTH_DATA_CONSENT COMMUNITY_CONSENT; do publish_text "$type" "Text of $type, run $run." > /dev/null; done
current() { get "$1" "/api/v1/policies/$2/current" | json 'd["versionId"]'; }
accept_required() { # accept_required <token>  -> prints the status
  local ids
  ids="$(for type in TERMS_OF_USE PRIVACY_POLICY HEALTH_DATA_CONSENT; do current "$1" "$type"; done | python3 -c 'import sys,json; print(json.dumps([l.strip() for l in sys.stdin if l.strip()]))')"
  req "$1" POST /api/v1/me/required-consents "{\"versionIds\":$ids,\"client\":\"WEB\"}"
  echo "$RCODE"
}

carol="val-carol-$run"
dave="val-dave-$run"
tc="$(token "$carol")"
td="$(token "$dave")"
carol_id="$(get "$tc" /api/v1/me | json 'd["id"]')"
dave_id="$(get "$td" /api/v1/me | json 'd["id"]')"
food="sentinela-alimento-$run"
answers='{"PREGNANT_OR_NURSING":"NO","CONDITION_REQUIRING_CARE":"NO","MEDICATION_AFFECTING_PLAN":"NO","EATING_DISORDER_HISTORY":"NO"}'
anamnesis() { # anamnesis <birthDate> [other text]
  echo "{\"objective\":\"GENERAL_HEALTH\",\"trainingLevel\":\"BEGINNER\",\"birthDate\":\"$1\",\"screening\":$answers,\"items\":[{\"kind\":\"DISLIKED_FOOD\",\"otherText\":\"${2:-$food}\"}]}"
}

echo "== E2-CA1, E2-CA3, E2-CA4: the barrier before health data, and the acceptance"
r="$(send "$tc" GET /api/v1/me/anamnesis)"
[[ "$(code_of "$r")" == 403 && "$(body_of "$r" | json 'd["code"]')" == consent_required ]] && ok "E2-CA1 without HEALTH_DATA_CONSENT the anamnesis is 403 consent_required" || bad "E2-CA1 barrier closed" "$r"
r="$(send "$tc" PUT /api/v1/me/anamnesis "$(anamnesis 1990-05-01)")"
[[ "$(code_of "$r")" == 403 ]] && ok "E2-CA1 saving is refused the same way" || bad "E2-CA1 write barrier" "$r"
[[ "$(status "$tc" /api/v1/catalogs/ALLERGY)" == 200 ]] && ok "E2-CA1 the catalogs do not need the consent (the person needs them to fill in the form)" || bad "E2-CA1 catalog"
[[ "$(get "$tc" /api/v1/me/consents | json 'sum(1 for i in d["items"] if i["pending"])')" == 3 ]] && ok "E2-CA4 a new person has the three required consents pending" || bad "E2-CA4 pending"
[[ "$(accept_required "$tc")" == 204 ]] && ok "E2-CA3 accepting the three required consents in the versions seen is 204" || bad "E2-CA3 accept"
[[ "$(status "$tc" /api/v1/me/anamnesis)" == 404 ]] && ok "E2-CA1 with the consent the barrier opens (404: no anamnesis yet)" || bad "E2-CA1 barrier open"
[[ "$(accept_required "$td")" == 204 ]] && ok "E2-CA3 a second person accepts too" || bad "E2-CA3 dave accepts"

echo "== E2-CA11, E2-CA12: anamnesis, revisions, screening"
r="$(send "$tc" PUT /api/v1/me/anamnesis "$(anamnesis 1990-05-01 | sed "s/\"BEGINNER\"/\"NOT_$run\"/")")"
if [[ "$(code_of "$r")" == 400 && "$(body_of "$r" | json 'd["code"]')" == anamnesis_invalid && "$(body_of "$r" | json '"traceId" in d')" == True && "$r" != *"NOT_$run"* ]]; then
  ok "E2-CA11 a value outside the catalog is 400 anamnesis_invalid with the field name, a traceId and no echo of the value"
else bad "E2-CA11 invalid" "$r"; fi
r="$(send "$tc" PUT /api/v1/me/anamnesis "$(anamnesis 2999-01-01)")"
[[ "$(code_of "$r")" == 400 ]] && ok "E2-CA11 a birth date in the future is 400" || bad "E2-CA11 future date" "$r"
r="$(send "$tc" PUT /api/v1/me/anamnesis "$(anamnesis 1990-05-01)")"
[[ "$(code_of "$r")" == 200 && "$(body_of "$r" | json 'd["revision"] == 1 and d["eligible"] is True')" == True ]] && ok "E2-CA11 a valid anamnesis is revision 1 and eligible" || bad "E2-CA11 save" "$r"
r="$(send "$tc" PUT /api/v1/me/anamnesis "$(anamnesis "$(python3 -c 'import datetime; print((datetime.date.today().replace(year=datetime.date.today().year-15)).isoformat())')")")"
[[ "$(code_of "$r")" == 200 && "$(body_of "$r" | json 'd["revision"] == 2 and d["eligible"] is False and d["ineligibilityReasons"] == ["UNDER_MINIMUM_AGE"]')" == True ]] && ok "E2-CA12 a minor is revision 2 and NOT eligible for the automatic plan" || bad "E2-CA12 minor" "$r"
[[ "$(get "$tc" /api/v1/me/anamnesis/revisions/1 | json 'd["eligible"]')" == True ]] && ok "E2-CA11 the first revision is still readable, as it was" || bad "E2-CA11 old revision"
audit="$(get "$tadmin" '/api/v1/admin/audit-events?limit=200')"
[[ "$(echo "$audit" | json 'any(e["action"] == "eligibility.changed" for e in d["items"])')" == True ]] && ok "E2-CA12 the mark is audited (eligibility.changed)" || bad "E2-CA12 audit"
r="$(send "$tc" PUT /api/v1/me/anamnesis "$(anamnesis 1990-05-01)")"
[[ "$(body_of "$r" | json 'd["revision"] == 3 and d["eligible"] is True')" == True ]] && ok "E2-CA12 redoing it with an eligible answer removes the mark" || bad "E2-CA12 reversal" "$r"
[[ "$(status "$td" /api/v1/me/anamnesis)" == 404 ]] && ok "E2-CA6 the other person sees none of it (404)" || bad "E2-CA6 isolation over the API"
[[ "$(psql_ -c "select count(*) from profile.anamnesis_revision where user_id = '$carol_id'")" == 3 ]] && ok "E2-CA11 three revisions exist in the database" || bad "E2-CA11 revisions in db"
req "$td" PUT /api/v1/me/anamnesis "$(anamnesis 1985-01-01 "dave-$food")"
[[ "$RCODE" == 200 ]] && ok "E2-CA6 the other person saves their own anamnesis" || bad "E2-CA6 dave saves" "$RCODE $RBODY"
[[ "$(get "$tc" /api/v1/me/anamnesis/revisions | json 'len(d["items"])')" == 3 && "$(get "$td" /api/v1/me/anamnesis/revisions | json 'len(d["items"])')" == 1 ]] && ok "E2-CA6 each person lists only their own revisions" || bad "E2-CA6 revision lists"

echo "== E2-CA3, E2-CA4: a new version asks again; optional consents turn on and off"
publish_text HEALTH_DATA_CONSENT "New text of the health data consent, run $run." > /dev/null
r="$(send "$tc" GET /api/v1/me/anamnesis)"
[[ "$(code_of "$r")" == 403 && "$(body_of "$r" | json 'd["code"]')" == consent_required ]] && ok "E2-CA3 a new version of the required text closes the barrier until the person accepts it" || bad "E2-CA3 new version" "$r"
[[ "$(accept_required "$tc")" == 204 && "$(status "$tc" /api/v1/me/anamnesis)" == 200 ]] && ok "E2-CA3 accepting the new version opens it again" || bad "E2-CA3 re-accept"
community="$(current "$tc" COMMUNITY_CONSENT)"
optional() { req "$tc" PUT /api/v1/me/consents/COMMUNITY_CONSENT "{\"granted\":$1,\"versionId\":\"$community\",\"client\":\"WEB\"}"; }
community_granted() { get "$tc" /api/v1/me/consents | json '[i for i in d["items"] if i["type"]=="COMMUNITY_CONSENT"][0]["granted"]'; }
optional true
[[ "$RCODE" == 204 ]] && ok "E2-CA4 an optional consent is turned on" || bad "E2-CA4 on" "$RCODE $RBODY"
[[ "$(community_granted)" == True ]] && ok "E2-CA4 and it shows as granted at once" || bad "E2-CA4 granted"
optional false
[[ "$(community_granted)" == False ]] && ok "E2-CA4 and revoking takes effect on the next call" || bad "E2-CA4 revoked"
req "$tc" PUT /api/v1/me/consents/TERMS_OF_USE "{\"granted\":false,\"versionId\":\"$(current "$tc" TERMS_OF_USE)\",\"client\":\"WEB\"}"
[[ "$RCODE" == 400 ]] && ok "E2-CA4 a required consent cannot be revoked through the optional route" || bad "E2-CA4 required" "$RCODE $RBODY"

echo "== E2-CA5, E2-CA8, E2-CA9: append-only proof, audit without values, sentinel"
out="$(psql_ -c "update consent.consent_event set client='MOBILE'" 2>&1)"
[[ "$out" == *"append-only"* ]] && ok "E2-CA5 the database refuses UPDATE on the proof of acceptance, even as superuser" || bad "E2-CA5 update refused" "$out"
out="$(psql_ -c "delete from profile.anamnesis_revision" 2>&1)"
[[ "$out" == *"append-only"* ]] && ok "E2-CA6 the database refuses DELETE on the anamnesis, even as superuser" || bad "E2-CA6 delete refused" "$out"
# Positive control: with the trigger switched off (inside a transaction that is rolled back) the same UPDATE goes through,
# so the check above fails when the rule is broken.
control="$(psql_ -c "begin; alter table consent.consent_event disable trigger consent_event_no_row_change; update consent.consent_event set client = client where user_id = '$carol_id'; rollback;" 2>&1)"
[[ "$control" == *"UPDATE"* && "$control" != *"append-only"* ]] && ok "E2-CA5 control: without the trigger the UPDATE is NOT refused, so the check above proves the rule" || bad "E2-CA5 control" "$control"
[[ "$(psql_ -c "select count(*) from platform.audit_event where fields::text like '%$food%' or entity_id like '%$food%' or action like '%$food%'")" == 0 ]] && ok "E2-CA8 the audit trail has no value of the anamnesis" || bad "E2-CA8 audit has the value"
[[ "$(psql_ -c "select count(*) from platform.outbox_message where payload::text like '%$food%'")" == 0 ]] && ok "E2-CA9 no queue message carries the value" || bad "E2-CA9 outbox has the value"
[[ "$(psql_ -c "select count(*) from consent.consent_event where user_id = '$carol_id'")" -ge 3 ]] && ok "E2-CA8 the acceptances are recorded as events, never as a flag" || bad "E2-CA8 events"

echo "== E2-CA13: the export has the person's data and nobody else's"
r="$(send "$tc" POST /api/v1/me/exports)"
export_task="$(body_of "$r" | json 'd["taskId"]')"
[[ "$(code_of "$r")" == 202 ]] && ok "E2-CA13 asking for the export is 202 with a task id" || bad "E2-CA13 request" "$r"
for _ in $(seq 1 40); do
  [[ "$(code_of "$(send "$tc" GET "/api/v1/me/exports/$export_task")")" == 200 ]] && break
  sleep 2
done
r="$(send "$tc" GET "/api/v1/me/exports/$export_task")"
url="$(body_of "$r" | json 'd["url"]')"
if [[ "$(code_of "$r")" == 200 && -n "$url" ]]; then
  ok "E2-CA13 the file became ready and the answer carries a link"
  # The link names the storage host of the compose network; reach it through the published port, keeping the signed Host.
  host="$(python3 -c 'import sys,urllib.parse as u; print(u.urlparse(sys.argv[1]).netloc)' "$url")"
  local_url="$(python3 -c 'import sys,urllib.parse as u; p=u.urlparse(sys.argv[1]); print(p._replace(netloc="localhost:"+sys.argv[2]).geturl())' "$url" "${HERACLES_S3_PORT:-4567}")"
  dl="$(curl -s -w '\n%{http_code}' -H "Host: $host" "$local_url")"
  file="$(sed '$d' <<< "$dl")"
  [[ "$file" == *"$food"* ]] && ok "E2-CA13 the file has the person's anamnesis" || bad "E2-CA13 file content" "http $(tail -n 1 <<< "$dl"), host $host, ${file:0:160}"
  [[ "$file" != *"dave-$food"* && "$file" != *"$dave_id"* ]] && ok "E2-CA13 the file has nothing of the other person" || bad "E2-CA13 leaks the other person"
  [[ "$(python3 -c 'import sys,json; d=json.loads(sys.stdin.read()); print(all(k in d for k in ["account","auditTrail","anamnesis","consents"]))' <<< "$file" 2>/dev/null)" == True ]] && ok "E2-CA13 the file has account, audit trail, anamnesis and consents" || bad "E2-CA13 sections"
else bad "E2-CA13 the export never became ready" "$r"; fi
[[ "$(status "$td" "/api/v1/me/exports/$export_task")" == 404 ]] && ok "E2-CA13 another person cannot get the file (404)" || bad "E2-CA13 isolation"

echo "== E2-CA14: erasure, with 7 days to change one's mind"
r="$(send "$tc" POST /api/v1/me/erasure-requests)"
[[ "$(code_of "$r")" == 202 && "$(body_of "$r" | json 'd["status"]')" == PENDING ]] && ok "E2-CA14 the request is accepted and pending" || bad "E2-CA14 request" "$r"
[[ "$(psql_ -c "select extract(day from due_at - requested_at) from platform.account_erasure where user_id = '$carol_id'")" == 7 ]] && ok "E2-CA14 it is due in 7 days" || bad "E2-CA14 7 days"
[[ "$(code_of "$(send "$tc" DELETE /api/v1/me/erasure-requests/current)")" == 204 && "$(status "$tc" /api/v1/me/anamnesis)" == 200 ]] && ok "E2-CA14 canceling keeps everything" || bad "E2-CA14 cancel"
send "$tc" POST /api/v1/me/erasure-requests > /dev/null
proofs_before="$(psql_ -c "select count(*) from consent.consent_event")"
# The 7 days are the unit tests' business (fixed clock); here the stored time is moved so the real scheduler finds it due.
psql_ -c "update platform.account_erasure set requested_at = now() - interval '8 days', due_at = now() - interval '1 minute' where user_id = '$carol_id'" > /dev/null
echo "   waiting for the scheduler and the task (up to about 2 minutes)"
for _ in $(seq 1 60); do
  [[ "$(psql_ -c "select status from platform.account_erasure where user_id = '$carol_id' and status <> 'CANCELED' order by requested_at desc limit 1")" == DONE ]] && break
  sleep 2
done
[[ "$(psql_ -c "select status from platform.account_erasure where user_id = '$carol_id' and status <> 'CANCELED' order by requested_at desc limit 1")" == DONE ]] && ok "E2-CA14 the task ran and the request is DONE" || bad "E2-CA14 erasure never finished"
[[ "$(psql_ -c "select count(*) from profile.anamnesis_revision where user_id = '$carol_id'")" == 0 ]] && ok "E2-CA14 the anamnesis and its revisions are gone" || bad "E2-CA14 anamnesis remains"
[[ "$(psql_ -c "select sub is null and deleted_at is not null from platform.app_user where id = '$carol_id'")" == t ]] && ok "E2-CA14 the account is emptied (no subject, no profile)" || bad "E2-CA14 account"
[[ "$(psql_ -c "select count(*) from consent.consent_event")" == "$proofs_before" && "$(psql_ -c "select count(*) from consent.consent_event where user_id = '$carol_id'")" == 0 ]] && ok "E2-CA14 the proof of acceptance stays, with no link to the person" || bad "E2-CA14 proof"
[[ "$(psql_ -c "select count(*) from platform.audit_event where actor_user_id = '$carol_id' or entity_id = '$carol_id'")" == 0 ]] && ok "E2-CA14 the audit trail no longer names the person" || bad "E2-CA14 audit link"
[[ "$(psql_ -c "select count(*) from platform.data_export where user_id = '$carol_id'")" == 0 ]] && ok "E2-CA14 the export file reference is gone" || bad "E2-CA14 export reference"
new_id="$(get "$tc" /api/v1/me | json 'd["id"]')"
[[ -n "$new_id" && "$new_id" != "$carol_id" ]] && ok "E2-CA14 the old token opens a NEW account (different id)" || bad "E2-CA14 new account" "$new_id"
[[ "$(get "$tc" /api/v1/me/consents | json 'sum(1 for i in d["items"] if i["pending"])')" == 3 && "$(status "$tc" /api/v1/me/anamnesis)" == 403 ]] && ok "E2-CA14 and it is empty: nothing accepted, nothing saved" || bad "E2-CA14 empty account"
[[ "$(psql_ -c "select count(*) from profile.anamnesis_revision where user_id = '$dave_id'")" == 1 && "$(psql_ -c "select count(*) from consent.consent_event where user_id = '$dave_id'")" -ge 3 ]] && ok "E2-CA14 the other person is untouched" || bad "E2-CA14 other person"
logs="$("${compose[@]}" logs backend --since "$since" 2>&1)"
[[ "$logs" != *"$food"* ]] && ok "E2-CA9 the anamnesis value is not in the application logs of this run" || bad "E2-CA9 value in logs"

echo
echo "$pass ok, $fail failed"
echo "Look at it yourself: Grafana http://localhost:${HERACLES_GRAFANA_PORT:-3001} (admin/admin), queues http://localhost:${HERACLES_SQS_UI_PORT:-9325}"
((fail == 0))
