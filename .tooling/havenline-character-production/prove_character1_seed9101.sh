#!/usr/bin/env bash
set -euo pipefail

character=Character1
out="character_output/${character}"
mkdir -p "$out"

test -s "$out/${character}_raw.glb"
test -s "$out/generation-report.json"
test -s "$out/approved_reference_sheet.jpg"

export LIBGL_ALWAYS_SOFTWARE=1
export MESA_LOADER_DRIVER_OVERRIDE=llvmpipe

blender --background --factory-startup \
  --python .tooling/havenline-character-production/remove_extreme_planar_faces.py -- \
  --character "$character" \
  --input "$out/${character}_raw.glb" \
  --output "$out"
test -s "$out/${character}_extreme_plane_clean.glb"

blender --background --factory-startup \
  --python .tooling/havenline-character-production/sanitize_character_mesh.py -- \
  --character "$character" \
  --input "$out/${character}_extreme_plane_clean.glb" \
  --output "$out"
test -s "$out/${character}_sanitized.glb"

blender --background --factory-startup \
  --python .tooling/havenline-girls-multiview/remove_spatial_outliers.py -- \
  --character "$character" \
  --input "$out/${character}_sanitized.glb" \
  --output "$out"
test -s "$out/${character}_spatial.glb"

blender --background --factory-startup \
  --python .tooling/havenline-character-production/prepare_mobile_source_mesh.py -- \
  --character "$character" \
  --input "$out/${character}_spatial.glb" \
  --output "$out"
test -s "$out/${character}_mobile_source.glb"

# Run the maintained repo-local rig so its adjacent refinement module remains importable.
blender --background --factory-startup \
  --python .tooling/havenline-character-production/rig_animate_character.py -- \
  --character "$character" \
  --input "$out/${character}_mobile_source.glb" \
  --output "$out"
test -s "$out/${character}_production.glb"
test -s "$out/${character}_production.fbx"
test -s "$out/${character}_LOD1.glb"
test -s "$out/${character}_LOD2.glb"

blender --background --factory-startup \
  --python .tooling/havenline-character-production/render_character_proofs_v4.py -- \
  --character "$character" \
  --input "$out/${character}_production.glb" \
  --output "$out"
for proof in front three-quarter side back; do
  test -s "$out/proof_${proof}.png"
done

python .tooling/havenline-character-production/validate_character_asset.py \
  --character "$character" \
  --directory "$out"

python - <<'PY'
import hashlib
import json
from pathlib import Path

root = Path('character_output/Character1')
plane = json.loads((root / 'extreme-planar-face-report.json').read_text())
sanitize = json.loads((root / 'mesh-sanitization-report.json').read_text())
spatial = json.loads((root / 'spatial-outlier-report.json').read_text())
mobile = json.loads((root / 'mobile-source-report.json').read_text())
rig = json.loads((root / 'rig-report.json').read_text())
proof = json.loads((root / 'proof-render-report.json').read_text())
validation = json.loads((root / 'validation-report.json').read_text())

checks = {
    'floor cleanup': plane.get('success') is True,
    'source axis': plane.get('selectedCandidate', {}).get('axis') == 'y',
    'standing orientation': sanitize.get('orientation', {}).get('standingAxisVerified') is True,
    'spatial cleanup': spatial.get('success') is True,
    'mobile reduction': mobile.get('success') is True,
    'rig': rig.get('success') is True,
    'proof render': proof.get('success') is True,
    'asset validation': validation.get('passed') is True,
}
failed = [name for name, passed in checks.items() if not passed]
if failed:
    details = '; '.join(validation.get('failures', []))
    raise SystemExit('Character 1 proof failed: ' + ', '.join(failed) + (f' ({details})' if details else ''))

faces = mobile.get('reduction', {}).get('totalFacesAfter', 0)
if not 14000 <= faces <= 39000:
    raise SystemExit(f'Character 1 mobile face count is unsafe: {faces}')
if plane.get('deletion', {}).get('facesRemoved', 0) < 3000:
    raise SystemExit('Character 1 floor cleanup removed too few faces')

production = root / 'Character1_production.glb'
status = {
    'schemaVersion': 4,
    'character': 'Character1',
    'seed': 9101,
    'sourceMode': 'actionless-v2-dominant-floor-cleaned-mobile-production',
    'floorFacesRemoved': plane.get('deletion', {}).get('facesRemoved'),
    'mobileSourceFaces': faces,
    'productionVertices': validation.get('metrics', {}).get('baseMesh', {}).get('vertices'),
    'productionGlbSha256': hashlib.sha256(production.read_bytes()).hexdigest(),
    'machinePassed': True,
    'approved': False,
    'unityIntegrated': False,
    'humanVisualApprovalRequired': True,
}
(root / 'actionless-seed9101-status.json').write_text(json.dumps(status, indent=2) + '\n')
print(json.dumps(status, indent=2))
PY
