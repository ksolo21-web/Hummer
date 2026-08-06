[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('Character3', 'Character4')]
    [string]$Character
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$RepoRoot = (Get-Location).Path
$ToolRoot = Join-Path $RepoRoot '.tooling\havenline-character-production'
$OutputRoot = Join-Path $RepoRoot "character_output\$Character"
$Sf3dOutput = Join-Path $RepoRoot "sf3d_output\$Character"
$RuntimeManifest = Join-Path $ToolRoot 'runtime-versions.json'
$GeneratorCommit = 'ff21fc491b4dc5314bf6734c7c0dabd86b5f5bb2'
$ModelId = 'stabilityai/stable-fast-3d'
$MinimumVramMiB = 6144

New-Item -ItemType Directory -Force -Path $OutputRoot | Out-Null

$status = [ordered]@{
    schemaVersion = 2
    character = $Character
    sourceMode = 'self-hosted-rtx-sf3d'
    generator = 'Stability-AI/stable-fast-3d'
    generatorCommit = $GeneratorCommit
    gpuPreflight = 'pending'
    environmentSetup = 'pending'
    reconstructOutcome = 'pending'
    sanitizeOutcome = 'pending'
    rigOutcome = 'pending'
    proofOutcome = 'pending'
    validateOutcome = 'pending'
    machinePassed = $false
    approved = $false
    humanVisualReviewStatus = 'pending'
    humanVisualApprovalRequired = $true
    unityIntegrated = $false
    failureReason = $null
}

function Save-Status {
    $path = Join-Path $OutputRoot 'sf3d-pipeline-status.json'
    $status | ConvertTo-Json -Depth 10 | Set-Content -Path $path -Encoding utf8
    Get-Content $path -Raw | Write-Host
}

function Assert-LastExitCode([string]$Label) {
    if ($LASTEXITCODE -ne 0) {
        throw "$Label failed with exit code $LASTEXITCODE."
    }
}

function Test-True([string]$Value) {
    return $Value -match '(?i)^(true|1|yes)$'
}

function Import-VisualStudioEnvironment {
    $vswhere = Join-Path ${env:ProgramFiles(x86)} 'Microsoft Visual Studio\Installer\vswhere.exe'
    if (-not (Test-Path $vswhere)) {
        throw 'Visual Studio Installer vswhere.exe was not found.'
    }
    $installation = (& $vswhere -latest -products * -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -property installationPath).Trim()
    if (-not $installation) {
        throw 'Visual Studio 2022 C++ build tools were not found.'
    }
    $vsDevCmd = Join-Path $installation 'Common7\Tools\VsDevCmd.bat'
    $environment = cmd.exe /s /c "`"$vsDevCmd`" -arch=x64 -host_arch=x64 >nul && set"
    Assert-LastExitCode 'Visual Studio developer environment initialization'
    foreach ($line in $environment) {
        if ($line -match '^([^=]+)=(.*)$') {
            Set-Item -Path "Env:$($Matches[1])" -Value $Matches[2]
        }
    }
    if (-not (Get-Command cl.exe -ErrorAction SilentlyContinue)) {
        throw 'Visual Studio C++ compiler cl.exe is not available after initialization.'
    }
}

function Get-BlenderExecutable {
    $toolCache = if ($env:RUNNER_TOOL_CACHE) {
        Join-Path $env:RUNNER_TOOL_CACHE 'havenline\blender-4.5.12'
    }
    else {
        Join-Path $env:RUNNER_TEMP 'havenline\blender-4.5.12'
    }
    $existing = Get-ChildItem $toolCache -Filter blender.exe -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($existing) {
        return $existing.FullName
    }

    New-Item -ItemType Directory -Force -Path $toolCache | Out-Null
    $archive = Join-Path $env:RUNNER_TEMP 'blender-4.5.12-windows-x64.zip'
    Invoke-WebRequest `
        -Uri 'https://download.blender.org/release/Blender4.5/blender-4.5.12-windows-x64.zip' `
        -OutFile $archive
    Expand-Archive -Path $archive -DestinationPath $toolCache -Force
    $blender = Get-ChildItem $toolCache -Filter blender.exe -Recurse | Select-Object -First 1
    if (-not $blender) {
        throw 'Portable Blender 4.5.12 executable was not found after extraction.'
    }
    return $blender.FullName
}

try {
    if (-not $env:HF_TOKEN) {
        throw 'HF_TOKEN is required for the gated Stable Fast 3D model.'
    }
    if (-not (Test-True $env:STABILITY_AI_LICENSE_ACCEPTED)) {
        throw 'Set STABILITY_AI_LICENSE_ACCEPTED=true after accepting the Stability AI Community License.'
    }
    if (-not (Test-True $env:STABILITY_AI_COMMERCIAL_REGISTRATION_CONFIRMED)) {
        throw 'Set STABILITY_AI_COMMERCIAL_REGISTRATION_CONFIRMED=true after completing Stability AI commercial-use registration.'
    }

    $nvidia = Get-Command nvidia-smi.exe -ErrorAction SilentlyContinue
    if (-not $nvidia) {
        throw 'nvidia-smi.exe is unavailable on the self-hosted Windows runner.'
    }
    $gpuName = ((& nvidia-smi.exe --query-gpu=name --format=csv,noheader | Select-Object -First 1) -join '').Trim()
    Assert-LastExitCode 'NVIDIA GPU name query'
    $vramText = ((& nvidia-smi.exe --query-gpu=memory.total --format=csv,noheader,nounits | Select-Object -First 1) -join '').Trim()
    Assert-LastExitCode 'NVIDIA VRAM query'
    $vramMiB = [int]$vramText
    if ($vramMiB -lt $MinimumVramMiB) {
        throw "Stable Fast 3D requires at least $MinimumVramMiB MiB VRAM; runner exposes $vramMiB MiB."
    }

    if (-not $env:CUDA_PATH -or -not (Test-Path (Join-Path $env:CUDA_PATH 'bin\nvcc.exe'))) {
        throw 'A CUDA toolkit with nvcc is required on the RTX runner.'
    }
    $env:CUDA_HOME = $env:CUDA_PATH
    $env:Path = "$(Join-Path $env:CUDA_PATH 'bin');$env:Path"
    Import-VisualStudioEnvironment

    $runtime = Get-Content $RuntimeManifest -Raw | ConvertFrom-Json
    if ($runtime.blender.version -ne '4.5.12') {
        throw "Unexpected Blender pin: $($runtime.blender.version)"
    }
    if ($runtime.stableFast3D.commit -ne $GeneratorCommit) {
        throw "Unexpected Stable Fast 3D pin: $($runtime.stableFast3D.commit)"
    }
    if ($runtime.stableFast3D.textureResolution -ne 2048 -or
        $runtime.stableFast3D.remeshOption -ne 'triangle' -or
        $runtime.stableFast3D.targetVertexCount -ne 42000) {
        throw 'Stable Fast 3D production settings do not match the locked runtime contract.'
    }
    if ($runtime.reconstruction.$Character.generator -ne 'Stability-AI/stable-fast-3d') {
        throw "$Character is not mapped to Stable Fast 3D in the runtime contract."
    }
    $status.gpu = [ordered]@{
        name = $gpuName
        vramMiB = $vramMiB
        minimumRequiredMiB = $MinimumVramMiB
    }
    $status.gpuPreflight = 'success'
    Save-Status

    $venv = Join-Path $RepoRoot '.venv-sf3d'
    if (Test-Path $venv) {
        Remove-Item $venv -Recurse -Force
    }
    python -m venv $venv
    Assert-LastExitCode 'Python virtual environment creation'
    $python = Join-Path $venv 'Scripts\python.exe'
    & $python -m pip install --upgrade pip 'setuptools==69.5.1' wheel ninja
    Assert-LastExitCode 'Python packaging tool installation'
    & $python -m pip install --index-url https://download.pytorch.org/whl/cu124 `
        'torch==2.4.1' 'torchvision==0.19.1'
    Assert-LastExitCode 'CUDA PyTorch installation'

    $sf3dRepo = Join-Path $RepoRoot 'stable-fast-3d'
    if (Test-Path $sf3dRepo) {
        Remove-Item $sf3dRepo -Recurse -Force
    }
    git clone https://github.com/Stability-AI/stable-fast-3d.git $sf3dRepo
    Assert-LastExitCode 'Stable Fast 3D clone'
    git -C $sf3dRepo checkout --detach $GeneratorCommit
    Assert-LastExitCode 'Stable Fast 3D revision checkout'
    $actualCommit = (git -C $sf3dRepo rev-parse HEAD).Trim()
    if ($actualCommit -ne $GeneratorCommit) {
        throw "Stable Fast 3D revision mismatch: $actualCommit"
    }

    Push-Location $sf3dRepo
    try {
        & $python -m pip install -r requirements.txt
        Assert-LastExitCode 'Stable Fast 3D requirements installation'
    }
    finally {
        Pop-Location
    }
    & $python -m pip install 'pygltflib==1.16.5' 'Pillow>=10.1,<12'
    Assert-LastExitCode 'HAVENLINE SF3D validation dependencies installation'

    & $python -c "import torch; assert torch.cuda.is_available(); p=torch.cuda.get_device_properties(0); assert p.total_memory >= 6*1024**3; print(torch.__version__, torch.version.cuda, p.name, p.total_memory)"
    Assert-LastExitCode 'CUDA PyTorch validation'
    & $python -c "from huggingface_hub import login; import os; login(token=os.environ['HF_TOKEN'], add_to_git_credential=False)"
    Assert-LastExitCode 'Hugging Face gated model login'
    $status.environmentSetup = 'success'
    Save-Status

    $reference = Join-Path $ToolRoot "references\$Character.jpg"
    $preparedInput = Join-Path $OutputRoot 'sf3d_input.png'
    & $python (Join-Path $ToolRoot 'prepare_sf3d_character_input.py') `
        --character $Character `
        --sheet $reference `
        --output $preparedInput
    Assert-LastExitCode 'Stable Fast 3D input preparation'
    Copy-Item $reference (Join-Path $OutputRoot 'approved_reference_sheet.jpg') -Force

    if (Test-Path $Sf3dOutput) {
        Remove-Item $Sf3dOutput -Recurse -Force
    }
    New-Item -ItemType Directory -Force -Path $Sf3dOutput | Out-Null
    $env:HF_HOME = if ($env:HF_HOME) { $env:HF_HOME } else { Join-Path $env:RUNNER_TEMP 'havenline-huggingface' }
    $env:TORCH_EXTENSIONS_DIR = if ($env:TORCH_EXTENSIONS_DIR) { $env:TORCH_EXTENSIONS_DIR } else { Join-Path $env:RUNNER_TEMP 'havenline-torch-extensions' }
    $env:PYTORCH_CUDA_ALLOC_CONF = 'expandable_segments:True'
    $env:HF_HUB_DISABLE_TELEMETRY = '1'

    Push-Location $sf3dRepo
    try {
        & $python run.py $preparedInput `
            --device cuda `
            --pretrained-model $ModelId `
            --texture-resolution 2048 `
            --remesh_option triangle `
            --target_vertex_count 42000 `
            --foreground-ratio 0.88 `
            --output-dir $Sf3dOutput
        Assert-LastExitCode 'Stable Fast 3D reconstruction'
    }
    finally {
        Pop-Location
    }

    & $python (Join-Path $ToolRoot 'finalize_sf3d_generation.py') `
        --character $Character `
        --sf3d-output $Sf3dOutput `
        --prepared-input $preparedInput `
        --destination $OutputRoot `
        --generator-commit $GeneratorCommit `
        --gpu-name $gpuName `
        --gpu-vram-mib $vramMiB
    Assert-LastExitCode 'Stable Fast 3D output verification'
    $status.reconstructOutcome = 'success'
    Save-Status

    $blender = Get-BlenderExecutable
    & $blender --background --factory-startup `
        --python-expr "import bpy; assert bpy.app.version[:3] == (4, 5, 12), bpy.app.version_string; print(bpy.app.version_string)"
    Assert-LastExitCode 'Blender 4.5.12 version validation'

    $raw = Join-Path $OutputRoot "${Character}_raw.glb"
    $sanitized = Join-Path $OutputRoot "${Character}_sanitized.glb"
    & $blender --background --factory-startup `
        --python (Join-Path $ToolRoot 'sanitize_character_mesh.py') -- `
        --character $Character `
        --input $raw `
        --output $OutputRoot
    Assert-LastExitCode 'Mesh sanitization'
    if (-not (Test-Path $sanitized)) {
        throw "Sanitized GLB is missing: $sanitized"
    }
    $status.sanitizeOutcome = 'success'
    Save-Status

    & $blender --background --factory-startup `
        --python (Join-Path $ToolRoot 'rig_animate_character_export_only.py') -- `
        --character $Character `
        --input $sanitized `
        --output $OutputRoot
    Assert-LastExitCode 'Rig, animation and production export'
    foreach ($name in @(
        "${Character}_production.glb",
        "${Character}_production.fbx",
        "${Character}_LOD1.glb",
        "${Character}_LOD2.glb"
    )) {
        $path = Join-Path $OutputRoot $name
        if (-not (Test-Path $path)) {
            throw "Production output is missing: $path"
        }
    }
    $status.rigOutcome = 'success'
    Save-Status

    $env:HAVENLINE_CYCLES_SAMPLES = '16'
    & $blender --background --factory-startup `
        --python (Join-Path $ToolRoot 'render_character_proofs_cpu.py') -- `
        --character $Character `
        --input (Join-Path $OutputRoot "${Character}_production.glb") `
        --output $OutputRoot
    Assert-LastExitCode 'Cycles CPU proof rendering'
    foreach ($view in @('front', 'three-quarter', 'side', 'back')) {
        $proof = Join-Path $OutputRoot "proof_$view.png"
        if (-not (Test-Path $proof)) {
            throw "Proof image is missing: $proof"
        }
    }
    $status.proofOutcome = 'success'
    Save-Status

    & $python (Join-Path $ToolRoot 'validate_character_asset.py') `
        --character $Character `
        --directory $OutputRoot
    Assert-LastExitCode 'Production character validation'
    $validationPath = Join-Path $OutputRoot 'validation-report.json'
    if (-not (Test-Path $validationPath)) {
        throw 'Validation report is missing.'
    }
    $validation = Get-Content $validationPath -Raw | ConvertFrom-Json
    if (-not $validation.passed) {
        throw 'Validation report did not pass.'
    }
    $status.validateOutcome = 'success'
    $status.machinePassed = $true
    Save-Status
}
catch {
    $status.failureReason = $_.Exception.Message
    Save-Status
    Write-Error $_
    exit 1
}
finally {
    Save-Status
}
