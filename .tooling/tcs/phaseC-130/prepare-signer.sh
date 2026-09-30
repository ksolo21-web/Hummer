#!/usr/bin/env bash
set -euo pipefail
mkdir -p evidence
pass='territory-phase-c-130'
alias='territory-private'
keyfile="$RUNNER_TEMP/territory-phase-c-130.p12"
keytool -genkeypair -noprompt -storetype PKCS12 -keystore "$keyfile" -storepass "$pass" -keypass "$pass" -alias "$alias" -dname "CN=Territory Private 1.3.0, O=KO Enterprises, C=US" -keyalg RSA -keysize 3072 -validity 3650
echo "TCS_RELEASE_KEYSTORE_PATH=$keyfile" >> "$GITHUB_ENV"
echo "TCS_RELEASE_STORE_PASSWORD=$pass" >> "$GITHUB_ENV"
echo "TCS_RELEASE_KEY_ALIAS=$alias" >> "$GITHUB_ENV"
echo "TCS_RELEASE_KEY_PASSWORD=$pass" >> "$GITHUB_ENV"
