#!/usr/bin/env bash
# Creates or updates the model-usage metrics and dashboard in Cloud Monitoring.
#
#   monitoring/apply.sh --account=<the account with access to day1sec>
#
# Safe to run again: each metric and the dashboard is updated in place. The metrics only
# count log lines written after they exist; Vertex AI's own numbers go back 6 weeks.
set -euo pipefail

PROJECT=day1sec
DASHBOARD_ID=journal-model-usage
HERE="$(cd "$(dirname "$0")" && pwd)"
GCLOUD=(gcloud --project "$PROJECT" --quiet "$@")

for file in "$HERE"/metrics/*.yaml; do
  name="$(basename "$file" .yaml)"
  if "${GCLOUD[@]}" logging metrics describe "$name" >/dev/null 2>&1; then
    "${GCLOUD[@]}" logging metrics update "$name" --config-from-file="$file" >/dev/null
    echo "Updated metric $name"
  else
    "${GCLOUD[@]}" logging metrics create "$name" --config-from-file="$file" >/dev/null
    echo "Created metric $name"
  fi
done

full_name="projects/$PROJECT/dashboards/$DASHBOARD_ID"
config="$(mktemp)"
trap 'rm -f "$config"' EXIT
if etag="$("${GCLOUD[@]}" monitoring dashboards describe "$full_name" --format='value(etag)' 2>/dev/null)"; then
  python3 -c 'import json,sys; d=json.load(open(sys.argv[1])); d["etag"]=sys.argv[2]; print(json.dumps(d))' \
    "$HERE/dashboard.json" "$etag" >"$config"
  "${GCLOUD[@]}" monitoring dashboards update "$full_name" --config-from-file="$config" >/dev/null
  echo "Updated dashboard"
else
  python3 -c 'import json,sys; d=json.load(open(sys.argv[1])); d["name"]=sys.argv[2]; print(json.dumps(d))' \
    "$HERE/dashboard.json" "$full_name" >"$config"
  "${GCLOUD[@]}" monitoring dashboards create --config-from-file="$config" >/dev/null
  echo "Created dashboard"
fi
echo "https://console.cloud.google.com/monitoring/dashboards/builder/$DASHBOARD_ID?project=$PROJECT"
