#!/usr/bin/env bash
# Secure one-time setup for Beyond the Panel / Ivy ElevenLabs integration.
# Requires GitHub CLI (`gh`) authenticated to ksolo21-web/Hummer.
# The API key is read without echo, sent directly to GitHub Actions Secrets,
# and is never written to this repository.

set -euo pipefail
REPO="ksolo21-web/Hummer"

command -v gh >/dev/null || { echo "GitHub CLI (gh) is not installed." >&2; exit 1; }
gh auth status >/dev/null

read -r -s -p "Paste the ElevenLabs API key: " ELEVENLABS_KEY
printf '\n'
[[ -n "$ELEVENLABS_KEY" ]] || { echo "No API key was entered." >&2; exit 1; }
printf '%s' "$ELEVENLABS_KEY" | gh secret set ELEVENLABS_API_KEY --repo "$REPO"
unset ELEVENLABS_KEY

read -r -p "Paste Ivy's ElevenLabs voice ID: " IVY_VOICE_ID
[[ -n "$IVY_VOICE_ID" ]] || { echo "No voice ID was entered." >&2; exit 1; }
gh variable set ELEVENLABS_IVY_VOICE_ID --repo "$REPO" --body "$IVY_VOICE_ID"
unset IVY_VOICE_ID

gh workflow run elevenlabs-ivy-smoke-test.yml --repo "$REPO"

echo "ElevenLabs credentials stored. The Ivy smoke test has started."
echo "Run: gh run list --repo $REPO --workflow elevenlabs-ivy-smoke-test.yml"
