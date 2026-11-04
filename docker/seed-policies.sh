#!/usr/bin/env bash
# Publishes, as an administrator, the policy texts that are missing in the LOCAL stack. A new environment has none (the
# migrations do not seed them), and without the three required ones nobody is asked to accept anything.
#
#   docker/seed-policies.sh
#
# Idempotent: a type that already has a published text is left alone. The texts are development placeholders, marked as
# such, with no legal validity. For a real environment the texts are written, reviewed by a lawyer and published in
# /admin/policies (docs/operacao/textos-iniciais.md). Local only: it promotes its own user to ADMIN straight in the database.
set -uo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
compose=(docker compose -f "$here/compose.yml")
api="http://localhost:${HERACLES_API_PORT:-8080}"
issuer="http://localhost:${HERACLES_OIDC_PORT:-8082}/default"
sub="seed-admin"

json() { python3 -c "import sys,json; d=json.load(sys.stdin); print($1)"; }

# A token for <sub> through the authorization-code + PKCE flow of the fake identity server.
token() {
  local redirect=http://localhost/cb verifier challenge url loc code
  verifier="$(openssl rand -base64 48 | tr -d '=+/\n' | cut -c1-64)"
  challenge="$(printf %s "$verifier" | openssl dgst -sha256 -binary | openssl base64 -A | tr '+/' '-_' | tr -d '=')"
  url="$issuer/authorize?client_id=heracles-api&response_type=code&redirect_uri=$redirect&scope=openid&state=s&code_challenge=$challenge&code_challenge_method=S256"
  loc="$(curl -s -o /dev/null -D - -X POST "$url" --data-urlencode "username=$1" --data-urlencode "claims=" | tr -d '\r' | awk 'tolower($1)=="location:"{print $2}')"
  code="$(printf %s "$loc" | sed -n 's/.*[?&]code=\([^&]*\).*/\1/p')"
  curl -s -X POST "$issuer/token" -d grant_type=authorization_code -d "code=$code" -d "redirect_uri=$redirect" \
    -d client_id=heracles-api -d "code_verifier=$verifier" | json 'd["access_token"]'
}

tok="$(token "$sub")"
[[ -n "$tok" ]] || { echo "could not get a token: is the stack up (docker compose -f docker/compose.yml up -d --wait)?"; exit 2; }
curl -s -o /dev/null -H "Authorization: Bearer $tok" "$api/api/v1/me" # first access creates the user
"${compose[@]}" exec -T postgres psql -U postgres -d heracles -v ON_ERROR_STOP=1 -q -c "update platform.app_user set role='ADMIN' where sub='$sub'" > /dev/null \
  || { echo "could not promote $sub to ADMIN"; exit 2; }

# (a function and not an associative array: the bash of macOS is 3.2)
name_of() {
  case "$1" in
    TERMS_OF_USE) echo "Termos de uso" ;;
    PRIVACY_POLICY) echo "Política de privacidade" ;;
    HEALTH_DATA_CONSENT) echo "Dados de saúde" ;;
    AI_EXAMS_CONSENT) echo "Uso de IA nos exames" ;;
    COMMUNITY_CONSENT) echo "Comunidade" ;;
    NOTIFICATIONS_CONSENT) echo "Notificações" ;;
    PROMOTIONAL_CONSENT) echo "Notificações promocionais" ;;
  esac
}
published=0
there=0
failed=0
for type in TERMS_OF_USE PRIVACY_POLICY HEALTH_DATA_CONSENT AI_EXAMS_CONSENT COMMUNITY_CONSENT NOTIFICATIONS_CONSENT PROMOTIONAL_CONSENT; do
  code="$(curl -s -o /dev/null -w '%{http_code}' -H "Authorization: Bearer $tok" "$api/api/v1/policies/$type/current")"
  if [[ "$code" == 200 ]]; then
    echo "there  $type"; there=$((there + 1)); continue
  fi
  body="$(python3 -c 'import json,sys; n=sys.argv[1]; print(json.dumps({"title": n + " (texto de desenvolvimento)", "markdown": "**TEXTO DE DESENVOLVIMENTO.** Sem validade jurídica: será substituído pelo texto revisado antes de qualquer uso real.\n\n- Este texto existe só para o ambiente local.\n- Os textos de verdade são publicados em /admin/policies."}))' "$(name_of "$type")")"
  draft="$(curl -s -X POST -H "Authorization: Bearer $tok" -H 'Content-Type: application/json' -d "$body" "$api/api/v1/admin/policies/$type/draft" | json 'd.get("id","")')"
  published_code="$(curl -s -o /dev/null -w '%{http_code}' -X POST -H "Authorization: Bearer $tok" "$api/api/v1/admin/policy-drafts/$draft/publish")"
  if [[ -n "$draft" && "$published_code" == 200 ]]; then echo "new    $type"; published=$((published + 1)); else echo "FAIL   $type (draft '$draft', publish $published_code)"; failed=$((failed + 1)); fi
done
echo "$published published, $there already there, $failed failed"
[[ "$failed" == 0 ]]
