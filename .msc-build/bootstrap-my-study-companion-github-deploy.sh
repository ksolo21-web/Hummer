#!/usr/bin/env bash
set -euo pipefail

PROJECT_ID='my-study-companion-abc01'
PROJECT_NUMBER='949620144971'
REPOSITORY='ksolo21-web/Hummer'
POOL_ID='github-actions'
PROVIDER_ID='github'
SERVICE_ACCOUNT_ID='msc-github-deploy'
SERVICE_ACCOUNT_EMAIL="${SERVICE_ACCOUNT_ID}@${PROJECT_ID}.iam.gserviceaccount.com"

command -v gcloud >/dev/null || { echo 'Run this script in Google Cloud Shell.' >&2; exit 1; }
gcloud config set project "$PROJECT_ID"

gcloud services enable \
  iam.googleapis.com \
  iamcredentials.googleapis.com \
  sts.googleapis.com \
  serviceusage.googleapis.com \
  firebase.googleapis.com \
  firebasehosting.googleapis.com \
  firebaserules.googleapis.com

if ! gcloud iam service-accounts describe "$SERVICE_ACCOUNT_EMAIL" --project "$PROJECT_ID" >/dev/null 2>&1; then
  gcloud iam service-accounts create "$SERVICE_ACCOUNT_ID" \
    --project "$PROJECT_ID" \
    --display-name 'My Study Companion GitHub deployment'
fi

for role in \
  roles/firebase.viewer \
  roles/firebasehosting.admin \
  roles/firebaserules.admin \
  roles/serviceusage.serviceUsageConsumer; do
  gcloud projects add-iam-policy-binding "$PROJECT_ID" \
    --member "serviceAccount:${SERVICE_ACCOUNT_EMAIL}" \
    --role "$role" \
    --condition=None \
    --quiet >/dev/null
done

if ! gcloud iam workload-identity-pools describe "$POOL_ID" \
  --project "$PROJECT_ID" --location global >/dev/null 2>&1; then
  gcloud iam workload-identity-pools create "$POOL_ID" \
    --project "$PROJECT_ID" \
    --location global \
    --display-name 'GitHub Actions'
fi

if ! gcloud iam workload-identity-pools providers describe "$PROVIDER_ID" \
  --project "$PROJECT_ID" --location global --workload-identity-pool "$POOL_ID" >/dev/null 2>&1; then
  gcloud iam workload-identity-pools providers create-oidc "$PROVIDER_ID" \
    --project "$PROJECT_ID" \
    --location global \
    --workload-identity-pool "$POOL_ID" \
    --display-name 'GitHub ksolo21-web Hummer' \
    --issuer-uri 'https://token.actions.githubusercontent.com' \
    --attribute-mapping 'google.subject=assertion.sub,attribute.actor=assertion.actor,attribute.repository=assertion.repository,attribute.ref=assertion.ref' \
    --attribute-condition "assertion.repository=='${REPOSITORY}'"
fi

MEMBER="principalSet://iam.googleapis.com/projects/${PROJECT_NUMBER}/locations/global/workloadIdentityPools/${POOL_ID}/attribute.repository/${REPOSITORY}"
gcloud iam service-accounts add-iam-policy-binding "$SERVICE_ACCOUNT_EMAIL" \
  --project "$PROJECT_ID" \
  --role roles/iam.workloadIdentityUser \
  --member "$MEMBER" \
  --quiet >/dev/null

PROVIDER="projects/${PROJECT_NUMBER}/locations/global/workloadIdentityPools/${POOL_ID}/providers/${PROVIDER_ID}"

gcloud iam workload-identity-pools providers describe "$PROVIDER_ID" \
  --project "$PROJECT_ID" --location global --workload-identity-pool "$POOL_ID" >/dev/null
gcloud iam service-accounts get-iam-policy "$SERVICE_ACCOUNT_EMAIL" \
  --project "$PROJECT_ID" --format=json >/dev/null

cat <<EOF
My Study Companion GitHub deployment identity is ready.
Project: ${PROJECT_ID}
Provider: ${PROVIDER}
Service account: ${SERVICE_ACCOUNT_EMAIL}
No service-account key was created.
The next PR #22 workflow run can deploy Firebase Hosting and Firestore rules.
EOF
