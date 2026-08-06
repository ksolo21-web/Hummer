#!/usr/bin/env bash
set -Eeuo pipefail

PROJECT_ID='my-study-companion-abc01'
EXPECTED_PROJECT_NUMBER='949620144971'
REPOSITORY='ksolo21-web/Hummer'
REPAIR_BRANCH='agent/kreativ-live-backend-repair'
POOL_ID='github-actions'
PROVIDER_ID='github'
DEPLOY_SERVICE_ACCOUNT_ID='msc-github-deploy'

fail() {
  printf 'ERROR: %s\n' "$*" >&2
  exit 1
}

command -v gcloud >/dev/null 2>&1 || fail 'Run this file in Google Cloud Shell.'
command -v git >/dev/null 2>&1 || fail 'git is required.'
command -v python3 >/dev/null 2>&1 || fail 'python3 is required.'

ACTIVE_ACCOUNT="$(gcloud auth list --filter=status:ACTIVE --format='value(account)' | head -n1)"
[ -n "$ACTIVE_ACCOUNT" ] || fail 'No active Google Cloud account is signed in.'

printf '\nKREATIV Studio live Firebase root repair\n'
printf 'Active Google account: %s\n' "$ACTIVE_ACCOUNT"
printf 'Target project: %s\n\n' "$PROJECT_ID"
printf 'This operation preserves the current My Study Companion rules, inserts only the managed KREATIV blocks, validates both rulesets, and rolls back a partial release failure.\n'
printf 'It creates no service-account key and does not modify either Android APK.\n\n'
read -r -p "Type ${PROJECT_ID} to authorize the guarded live repair: " CONFIRMATION
[ "$CONFIRMATION" = "$PROJECT_ID" ] || fail 'Project confirmation did not match. Nothing was changed.'

gcloud config set project "$PROJECT_ID" >/dev/null
PROJECT_NUMBER="$(gcloud projects describe "$PROJECT_ID" --format='value(projectNumber)')"
[ "$PROJECT_NUMBER" = "$EXPECTED_PROJECT_NUMBER" ] || fail "Unexpected project number: $PROJECT_NUMBER"

printf '\nEnabling the narrowly required Google/Firebase APIs...\n'
gcloud services enable \
  iam.googleapis.com \
  iamcredentials.googleapis.com \
  sts.googleapis.com \
  serviceusage.googleapis.com \
  firebase.googleapis.com \
  firebaserules.googleapis.com \
  --project "$PROJECT_ID" >/dev/null

DEPLOY_SERVICE_ACCOUNT="${DEPLOY_SERVICE_ACCOUNT_ID}@${PROJECT_ID}.iam.gserviceaccount.com"
if ! gcloud iam service-accounts describe "$DEPLOY_SERVICE_ACCOUNT" \
  --project "$PROJECT_ID" >/dev/null 2>&1; then
  gcloud iam service-accounts create "$DEPLOY_SERVICE_ACCOUNT_ID" \
    --project "$PROJECT_ID" \
    --display-name 'My Study Companion GitHub deployment' >/dev/null
fi

# Minimal future deployment permissions: read Firebase app identity, maintain the
# registered OAuth/SHA configuration, publish Firebase Rules, and consume APIs.
for role in \
  roles/firebase.viewer \
  roles/oauthconfig.editor \
  roles/firebaserules.admin \
  roles/serviceusage.serviceUsageConsumer; do
  gcloud projects add-iam-policy-binding "$PROJECT_ID" \
    --member "serviceAccount:${DEPLOY_SERVICE_ACCOUNT}" \
    --role "$role" \
    --condition=None \
    --quiet >/dev/null
done

wait_for_pool() {
  local attempt
  for attempt in $(seq 1 30); do
    if gcloud iam workload-identity-pools describe "$POOL_ID" \
      --project "$PROJECT_ID" --location global >/dev/null 2>&1; then
      return 0
    fi
    sleep 2
  done
  return 1
}

if ! gcloud iam workload-identity-pools describe "$POOL_ID" \
  --project "$PROJECT_ID" --location global >/dev/null 2>&1; then
  # Recover a soft-deleted pool when present; otherwise create it.
  if gcloud iam workload-identity-pools undelete "$POOL_ID" \
    --project "$PROJECT_ID" --location global --quiet >/dev/null 2>&1; then
    wait_for_pool || fail 'The Workload Identity pool did not recover in time.'
  else
    gcloud iam workload-identity-pools create "$POOL_ID" \
      --project "$PROJECT_ID" --location global \
      --display-name 'GitHub Actions' >/dev/null
  fi
fi

gcloud iam workload-identity-pools update "$POOL_ID" \
  --project "$PROJECT_ID" --location global \
  --display-name 'GitHub Actions' --no-disabled >/dev/null

wait_for_provider() {
  local attempt
  for attempt in $(seq 1 30); do
    if gcloud iam workload-identity-pools providers describe "$PROVIDER_ID" \
      --project "$PROJECT_ID" --location global \
      --workload-identity-pool "$POOL_ID" >/dev/null 2>&1; then
      return 0
    fi
    sleep 2
  done
  return 1
}

if ! gcloud iam workload-identity-pools providers describe "$PROVIDER_ID" \
  --project "$PROJECT_ID" --location global \
  --workload-identity-pool "$POOL_ID" >/dev/null 2>&1; then
  # Recover a soft-deleted provider when present; otherwise create it.
  if gcloud iam workload-identity-pools providers undelete "$PROVIDER_ID" \
    --project "$PROJECT_ID" --location global \
    --workload-identity-pool "$POOL_ID" --quiet >/dev/null 2>&1; then
    wait_for_provider || fail 'The Workload Identity provider did not recover in time.'
  else
    gcloud iam workload-identity-pools providers create-oidc "$PROVIDER_ID" \
      --project "$PROJECT_ID" --location global \
      --workload-identity-pool "$POOL_ID" \
      --display-name 'GitHub Hummer' \
      --issuer-uri 'https://token.actions.githubusercontent.com' \
      --attribute-mapping 'google.subject=assertion.sub,attribute.actor=assertion.actor,attribute.repository=assertion.repository,attribute.ref=assertion.ref' \
      --attribute-condition "assertion.repository=='${REPOSITORY}'" >/dev/null
  fi
fi

gcloud iam workload-identity-pools providers update-oidc "$PROVIDER_ID" \
  --project "$PROJECT_ID" --location global \
  --workload-identity-pool "$POOL_ID" \
  --display-name 'GitHub Hummer' \
  --issuer-uri 'https://token.actions.githubusercontent.com' \
  --attribute-mapping 'google.subject=assertion.sub,attribute.actor=assertion.actor,attribute.repository=assertion.repository,attribute.ref=assertion.ref' \
  --attribute-condition "assertion.repository=='${REPOSITORY}'" \
  --no-disabled >/dev/null

MEMBER="principalSet://iam.googleapis.com/projects/${PROJECT_NUMBER}/locations/global/workloadIdentityPools/${POOL_ID}/attribute.repository/${REPOSITORY}"
gcloud iam service-accounts add-iam-policy-binding "$DEPLOY_SERVICE_ACCOUNT" \
  --project "$PROJECT_ID" \
  --role roles/iam.workloadIdentityUser \
  --member "$MEMBER" \
  --quiet >/dev/null

WORK_DIR="$(mktemp -d)"
cleanup() {
  rm -rf "$WORK_DIR"
}
trap cleanup EXIT

git clone --quiet --depth 1 --branch "$REPAIR_BRANCH" \
  "https://github.com/${REPOSITORY}.git" "$WORK_DIR/Hummer"
cd "$WORK_DIR/Hummer"
mkdir -p dist firebase/generated

export FIREBASE_ACCESS_TOKEN="$(gcloud auth print-access-token)"
[ -n "$FIREBASE_ACCESS_TOKEN" ] || fail 'Google Cloud did not provide an access token.'

printf '\nReading the live shared rules, validating the merged rules, and applying the KREATIV-only blocks...\n'
python3 scripts/kreativ_firebase_repair.py \
  --project-id "$PROJECT_ID" \
  --android-app-id '1:949620144971:android:63fec0446e8549d25946ce' \
  --package-name 'com.kreativstudio.app' \
  --storage-bucket 'my-study-companion-abc01.firebasestorage.app' \
  --sha1 '5C:79:A8:F7:52:9B:0A:93:A2:69:AA:B1:67:6B:AE:9B:CE:B6:14:59' \
  --sha256 '07:F0:25:19:C0:BD:05:62:F8:88:B2:FB:F1:F2:9B:1E:0B:43:1D:39:B9:1B:36:8C:72:04:29:D7:5D:84:D2:8D' \
  --firestore-fragment firebase/firestore.rules \
  --storage-fragment firebase/storage.rules \
  --generated-firestore firebase/generated/shared-firestore.rules \
  --generated-storage firebase/generated/shared-storage.rules \
  --report dist/firebase-repair-report.json \
  --apply

cp dist/firebase-repair-report.json "$HOME/KREATIV-Firebase-Repair-Report.json"

printf '\nSUCCESS: KREATIV Firestore and Storage authorization was merged into the live shared project.\n'
printf 'My Study Companion rules were preserved by the guarded merge.\n'
printf 'Evidence: %s\n' "$HOME/KREATIV-Firebase-Repair-Report.json"
printf 'On the installed KREATIV app, tap Retry cloud connection. No APK reinstall is required.\n'
