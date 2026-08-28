# Secure one-time setup for Beyond the Panel / Ivy ElevenLabs integration.
# Requires GitHub CLI (`gh`) authenticated to ksolo21-web/Hummer.
# The API key is entered locally, sent directly to GitHub Actions Secrets,
# and is never written to this repository or echoed to the terminal.

$ErrorActionPreference = "Stop"
$Repo = "ksolo21-web/Hummer"

if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
    throw "GitHub CLI (gh) is not installed or is not on PATH."
}

gh auth status | Out-Null

$secureKey = Read-Host "Paste the ElevenLabs API key" -AsSecureString
$keyPtr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureKey)
try {
    $plainKey = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($keyPtr)
    if ([string]::IsNullOrWhiteSpace($plainKey)) {
        throw "No API key was entered."
    }
    $plainKey | gh secret set ELEVENLABS_API_KEY --repo $Repo
    if ($LASTEXITCODE -ne 0) {
        throw "GitHub rejected the ELEVENLABS_API_KEY secret."
    }
}
finally {
    if ($keyPtr -ne [IntPtr]::Zero) {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($keyPtr)
    }
    $plainKey = $null
    $secureKey.Dispose()
}

$voiceId = Read-Host "Paste Ivy's ElevenLabs voice ID"
if ([string]::IsNullOrWhiteSpace($voiceId)) {
    throw "No voice ID was entered."
}

gh variable set ELEVENLABS_IVY_VOICE_ID --repo $Repo --body $voiceId
if ($LASTEXITCODE -ne 0) {
    throw "GitHub rejected the ELEVENLABS_IVY_VOICE_ID variable."
}

gh workflow run elevenlabs-ivy-smoke-test.yml --repo $Repo
if ($LASTEXITCODE -ne 0) {
    throw "The credentials were stored, but the smoke-test workflow could not be started."
}

Write-Host "ElevenLabs credentials stored. The Ivy smoke test has started." -ForegroundColor Green
Write-Host "Run: gh run list --repo $Repo --workflow elevenlabs-ivy-smoke-test.yml" -ForegroundColor Cyan
