# HAVENLINE — 2D to 3D Character Production Pipeline

Status: ACTIVE R&D / PRODUCTION AUDITION
Reference: `HAVENLINE_WHITEOUT_REFERENCE_LOCK.md`

## Goal

Convert the approved Havenline 2D character art into high-quality stylized 3D game characters while preserving the exact approved identity, proportions, winter clothing, backpack/accessory layout and premium Whiteout-like presentation.

An 8K result means **8K authoring masters** for texture preservation and rebaking. Android runtime textures are derived 2K/4K ASTC assets unless shipping-camera evidence proves a higher runtime resolution is necessary.

## Existing source advantage

The project already has multi-angle source sheets, so generation must not be judged from a single front view alone.

Authoritative references currently include:

- `image-gen-1(20260806-004835).png` — Character 1 turnaround: front / 3/4 / side / back.
- `image-gen-2(20260806-004837).png` — Character 2 turnaround: front / 3/4 / side / back.
- `Expedition Girl Character Turnaround.png` — Character 3 turnaround.
- `Expedition Girl Character Turnaround(1).png` — Character 4 turnaround.
- `Winter Explorer Girl with Camping Gear.png` and `Winter Explorer Character with Backpack and Firewood.png` — approved high-detail source portraits.

Any generated side/back detail that conflicts with these references is wrong even if the generated model looks attractive.

## Production lane A — permissive local reconstruction

### P0: TripoSR
Source: https://github.com/VAST-AI-Research/TripoSR
License: MIT for source + pretrained model.
Observed hardware: about 6 GB VRAM for one-image inference.
Outputs: OBJ/GLB; vertex color or baked texture atlas; configurable texture atlas resolution.

Why it is first:
- fully local,
- permissive license,
- fits consumer NVIDIA hardware,
- fast enough to audition all four characters repeatedly,
- no `nvdiffrast` dependency in the official requirements,
- output can be edited in Blender before final texture projection and rigging.

Use:
1. Generate geometry from the cleanest front/3/4 character image.
2. Render generated mesh at the exact reference side/back angles.
3. Compare silhouette against the approved turnarounds.
4. Reject or sculpt-correct invented anatomy/clothing/backpack details.
5. Do not trust generated texture as the final master.

### P1: Stable Fast 3D
Source: https://github.com/Stability-AI/stable-fast-3d
License: Stability AI Community License.
Observed hardware: about 6 GB VRAM for one-image inference.
Commercial gate: free commercial use is currently limited to qualifying users/entities under USD $1M annual revenue and requires Stability registration; re-check before production use.
Outputs: GLB with UVs/material parameters; configurable texture resolution; optional triangle/quad remeshing.

Use as a geometry/PBR comparison against TripoSR. Do not make the whole pipeline dependent on it until the commercial-license gate is recorded in the project license ledger.

### P1 cloud audition: Meshy free tier
Source: https://www.meshy.ai/
Current free-plan output license: CC BY 4.0 with attribution.
Use: rapid identity/geometry comparison, auto-rig experimentation and Unity-ready export.
Limits: latest Meshy model downloads / multi-view / 8K features may require a paid tier; never call a paid-only feature free.

Free-plan outputs may be commercially usable with required attribution, but the project should prefer the local permissive lane when comparable quality is possible.

## Rigging lane

### P0: UniRig
Source: https://github.com/VAST-AI-Research/UniRig
License: MIT.
Observed generation requirement: CUDA GPU with at least 8 GB VRAM.
Inputs: OBJ / FBX / GLB / VRM.
Outputs: predicted skeleton, skinning and merged rigged model.

Workflow:
1. Feed cleaned final mesh to UniRig.
2. Inspect skeleton before skinning.
3. Correct missing/poorly placed joints before generating final skin weights.
4. Export FBX/GLB.
5. Map to Unity Humanoid.
6. Validate locomotion + Havenline stress poses before production promotion.

Mixamo remains a secondary animation/retarget source, not the identity/model generator.

## 8K master texture lane

No current free image-to-3D generator should be trusted to invent a genuine 8K-quality character surface from one image. Texture fidelity must come back from the approved source artwork.

### Master workflow

1. Finalize topology and UVs in Blender.
2. Create an `8192 x 8192` authoring atlas for the hero character, or multiple 8K UDIM-style source maps if materially useful during authoring.
3. Camera-project the approved FRONT / SIDE / BACK art onto the cleaned mesh.
4. Blend seams manually or procedurally in Blender.
5. Preserve jacket orange trim, blue fabric, fur, leather backpack, boot details, glasses/hair and face identity from the approved artwork.
6. Build/bake:
   - 8K base color master,
   - 8K normal master,
   - 8K roughness master,
   - AO/cavity master,
   - optional mask map for Unity material variation.
7. Use source-derived detail instead of hallucinating new seams, buckles or gear.
8. Export runtime 4K/2K ASTC variants and compare in the shipping camera.

Real-ESRGAN or other permissively licensed image upscalers may be used only as controlled detail-assistance; they do not replace real geometry, UVs or source-view projection.

## Research-only / blocked candidates

### CharacterGen
Source: https://github.com/zjp-shadow/CharacterGen
Strength: specifically designed for stylized/anime-like 3D characters; Apache-2.0 repo/weights; useful R&D benchmark.
BLOCKER: official requirements include NVIDIA `nvdiffrast`. The current NVIDIA Source Code License restricts `nvdiffrast` to non-commercial research/evaluation. Therefore do not use the released pipeline to create commercial Havenline production assets unless that dependency is replaced with a commercially permitted implementation or NVIDIA grants appropriate rights.

### Unique3D
Source: https://github.com/AiuniAI/Unique3D
Strength: excellent high-fidelity single-image geometry and textures; MIT top-level license; front-facing rest-pose images are recommended by its authors.
BLOCKER: official requirements also include `nvdiffrast`, so treat the released pipeline as R&D/non-commercial until the rasterizer is replaced/licensed.

### TRELLIS.2 native PBR path
Source: https://github.com/microsoft/TRELLIS.2
Strength: very high-fidelity geometry and PBR generation.
BLOCKER: native PBR/render path uses NVIDIA `nvdiffrast`/`nvdiffrec`, which carry non-commercial use restrictions. Also significantly heavier hardware than the P0 local lane. Do not use its native material pipeline for production without resolving those dependencies.

### Hunyuan3D 2.1
Source: https://github.com/Tencent-Hunyuan/Hunyuan3D-2.1
Strength: high-fidelity shape + PBR; source/weights available.
BLOCKER: community license expressly excludes use/output outside its defined territory (EU, UK and South Korea are excluded), creating an unacceptable global-shipping risk for Havenline. Do not use for production assets unless licensing changes or separate rights are obtained.

## Character acceptance gates

A generated character is rejected if any of these fail:

- face no longer reads as the approved character,
- character becomes photoreal instead of premium stylized 3D,
- head/body proportion changes materially,
- hair silhouette changes materially,
- jacket/trim/fur silhouette changes materially,
- backpack/bedroll/mug/tool arrangement changes without design approval,
- hands/feet collapse or become toy/block geometry,
- side/back views disagree with approved turnarounds,
- topology cannot deform cleanly through gameplay poses,
- UV seams are obvious at shipping camera distance,
- textures read flat/plastic under Havenline URP lighting,
- model cannot be reduced through LODs without losing the character identity.

## Unity production targets

Authoring:
- 8K texture masters retained outside or excluded from Android runtime import as appropriate.
- High-quality LOD0 retained for review/cinematics/close proof.

Runtime starting targets (must be validated, not blindly enforced):
- LOD0: hero close-camera quality.
- LOD1: normal gameplay isometric quality.
- LOD2: helper/crowd/distant quality.
- 4K maximum hero runtime map only where visible benefit survives the shipping camera.
- 2K or lower for normal isometric states where visually equivalent.
- ASTC on supported Android targets.
- Unity Humanoid rig + existing Havenline animation contracts.

## Audition sequence

1. Character 1: TripoSR geometry baseline.
2. Character 1: Stable Fast 3D comparison if license gate is satisfied for evaluation.
3. Character 1: optional Meshy-free comparison.
4. Render neutral front/3q/side/back against the approved turnaround.
5. Correct topology/silhouette in Blender.
6. Project approved art to an 8K master atlas.
7. UniRig skeleton + skinning.
8. Unity Humanoid import.
9. Existing Havenline stress-pose / animation-clipping suite.
10. Exact Whiteout-style shipping-camera gameplay render.
11. Human visual approval before repeating the winning workflow on Characters 2–4.

Passing a machine rig/texture gate is not sufficient. The generated character must look like the approved Havenline character inside the actual game.
