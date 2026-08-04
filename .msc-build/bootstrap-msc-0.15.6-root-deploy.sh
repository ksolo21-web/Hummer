#!/usr/bin/env bash
set -euo pipefail

PROJECT_ID='my-study-companion-abc01'
REPOSITORY='ksolo21-web/Hummer'
REGION='us-central1'
POOL_ID='github-actions'
PROVIDER_ID='github'
DEPLOY_SERVICE_ACCOUNT_ID='msc-github-deploy'
RUNTIME_SERVICE_ACCOUNT_ID='msc-backend-runtime'
ARTIFACT_REPOSITORY='msc-containers'

command -v gcloud >/dev/null || {
  echo 'Run this script in Google Cloud Shell while signed in as the project owner.' >&2
  exit 1
}

gcloud config set project "$PROJECT_ID" >/dev/null
PROJECT_NUMBER="$(gcloud projects describe "$PROJECT_ID" --format='value(projectNumber)')"
DEPLOY_SERVICE_ACCOUNT="${DEPLOY_SERVICE_ACCOUNT_ID}@${PROJECT_ID}.iam.gserviceaccount.com"
RUNTIME_SERVICE_ACCOUNT="${RUNTIME_SERVICE_ACCOUNT_ID}@${PROJECT_ID}.iam.gserviceaccount.com"

printf 'Enabling required Google Cloud and Firebase APIs...\n'
gcloud services enable \
  iam.googleapis.com \
  iamcredentials.googleapis.com \
  sts.googleapis.com \
  serviceusage.googleapis.com \
  cloudresourcemanager.googleapis.com \
  artifactregistry.googleapis.com \
  run.googleapis.com \
  firestore.googleapis.com \
  identitytoolkit.googleapis.com \
  firebase.googleapis.com \
  firebasehosting.googleapis.com \
  firebaserules.googleapis.com \
  --project "$PROJECT_ID" >/dev/null

for account in "$DEPLOY_SERVICE_ACCOUNT_ID" "$RUNTIME_SERVICE_ACCOUNT_ID"; do
  email="${account}@${PROJECT_ID}.iam.gserviceaccount.com"
  if ! gcloud iam service-accounts describe "$email" --project "$PROJECT_ID" >/dev/null 2>&1; then
    gcloud iam service-accounts create "$account" \
      --project "$PROJECT_ID" \
      --display-name "My Study Companion ${account}" >/dev/null
  fi
done

if ! gcloud artifacts repositories describe "$ARTIFACT_REPOSITORY" \
  --project "$PROJECT_ID" --location "$REGION" >/dev/null 2>&1; then
  gcloud artifacts repositories create "$ARTIFACT_REPOSITORY" \
    --project "$PROJECT_ID" \
    --location "$REGION" \
    --repository-format docker \
    --description 'My Study Companion private release containers' >/dev/null
fi

for role in \
  roles/run.admin \
  roles/artifactregistry.writer \
  roles/firebase.viewer \
  roles/firebasehosting.admin \
  roles/firebaserules.admin \
  roles/serviceusage.serviceUsageConsumer; do
  gcloud projects add-iam-policy-binding "$PROJECT_ID" \
    --member "serviceAccount:${DEPLOY_SERVICE_ACCOUNT}" \
    --role "$role" \
    --condition=None \
    --quiet >/dev/null
done

for role in roles/datastore.user roles/logging.logWriter roles/monitoring.metricWriter; do
  gcloud projects add-iam-policy-binding "$PROJECT_ID" \
    --member "serviceAccount:${RUNTIME_SERVICE_ACCOUNT}" \
    --role "$role" \
    --condition=None \
    --quiet >/dev/null
done

gcloud iam service-accounts add-iam-policy-binding "$RUNTIME_SERVICE_ACCOUNT" \
  --project "$PROJECT_ID" \
  --member "serviceAccount:${DEPLOY_SERVICE_ACCOUNT}" \
  --role roles/iam.serviceAccountUser \
  --quiet >/dev/null

if ! gcloud iam workload-identity-pools describe "$POOL_ID" \
  --project "$PROJECT_ID" --location global >/dev/null 2>&1; then
  gcloud iam workload-identity-pools create "$POOL_ID" \
    --project "$PROJECT_ID" \
    --location global \
    --display-name 'GitHub Actions' >/dev/null
fi

if gcloud iam workload-identity-pools providers describe "$PROVIDER_ID" \
  --project "$PROJECT_ID" --location global \
  --workload-identity-pool "$POOL_ID" >/dev/null 2>&1; then
  gcloud iam workload-identity-pools providers update-oidc "$PROVIDER_ID" \
    --project "$PROJECT_ID" \
    --location global \
    --workload-identity-pool "$POOL_ID" \
    --issuer-uri 'https://token.actions.githubusercontent.com' \
    --attribute-mapping 'google.subject=assertion.sub,attribute.actor=assertion.actor,attribute.repository=assertion.repository,attribute.ref=assertion.ref' \
    --attribute-condition "assertion.repository=='${REPOSITORY}'" >/dev/null
else
  gcloud iam workload-identity-pools providers create-oidc "$PROVIDER_ID" \
    --project "$PROJECT_ID" \
    --location global \
    --workload-identity-pool "$POOL_ID" \
    --display-name 'GitHub ksolo21-web Hummer' \
    --issuer-uri 'https://token.actions.githubusercontent.com' \
    --attribute-mapping 'google.subject=assertion.sub,attribute.actor=assertion.actor,attribute.repository=assertion.repository,attribute.ref=assertion.ref' \
    --attribute-condition "assertion.repository=='${REPOSITORY}'" >/dev/null
fi

MEMBER="principalSet://iam.googleapis.com/projects/${PROJECT_NUMBER}/locations/global/workloadIdentityPools/${POOL_ID}/attribute.repository/${REPOSITORY}"
gcloud iam service-accounts add-iam-policy-binding "$DEPLOY_SERVICE_ACCOUNT" \
  --project "$PROJECT_ID" \
  --role roles/iam.workloadIdentityUser \
  --member "$MEMBER" \
  --quiet >/dev/null

PROVIDER="projects/${PROJECT_NUMBER}/locations/global/workloadIdentityPools/${POOL_ID}/providers/${PROVIDER_ID}"
gcloud iam workload-identity-pools providers describe "$PROVIDER_ID" \
  --project "$PROJECT_ID" --location global \
  --workload-identity-pool "$POOL_ID" >/dev/null

gcloud iam service-accounts get-iam-policy "$DEPLOY_SERVICE_ACCOUNT" \
  --project "$PROJECT_ID" --format=json >/dev/null

cat <<STATUS

My Study Companion 0.15.6 root-fix deployment identity is ready.
Project: ${PROJECT_ID}
Provider: ${PROVIDER}
Deployment service account: ${DEPLOY_SERVICE_ACCOUNT}
Runtime service account: ${RUNTIME_SERVICE_ACCOUNT}
Container repository: ${REGION}-docker.pkg.dev/${PROJECT_ID}/${ARTIFACT_REPOSITORY}
No service-account key was created.
STATUS
