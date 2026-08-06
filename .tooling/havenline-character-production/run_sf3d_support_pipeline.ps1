[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('Character1', 'Character3', 'Character4')]
    [string]$Character
)

$ErrorActionPreference = 'Stop'
$pipeline = Join-Path $PSScriptRoot 'run_sf3d_character_pipeline.ps1'
if (-not (Test-Path $pipeline)) {
    throw "Generic HAVENLINE SF3D pipeline is missing: $pipeline"
}

& $pipeline -Character $Character
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}
