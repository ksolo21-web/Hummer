# HAVENLINE — Human Reconstruction / 8K Character Deep Audit

Date: 2026-08-15
Status: ACTIVE
Reference lock: `HAVENLINE_WHITEOUT_REFERENCE_LOCK.md`

## What the existing Havenline art gives us

The four principal characters already have authoritative multi-view turnarounds. This is a major advantage over ordinary single-image reconstruction. A generated character is not allowed to invent a different side/back silhouette merely because the front view looked good.

Production must use all available front / 3-quarter / side / back evidence for visual validation and texture reprojection.

## Best practical free production stack right now

### 1. TripoSR — triangle-mesh baseline

Source: https://github.com/VAST-AI-Research/TripoSR
License: MIT.
Why it stays P0: local, permissive, ~6 GB single-image inference, conventional OBJ/GLB output, editable in Blender, configurable baked texture atlas, and no `nvdiffrast` dependency in the official requirements.

Role: create the first usable triangle-mesh body/clothing volume from the cleanest approved image. It is a starting mesh, not the final identity.

### 2. LHM++ SMPLX-FREE — multi-view 3D reference, not shipping mesh

Source: https://github.com/aigc3d/LHM-plusplus
Top-level code license: Apache-2.0.
Hardware: current models support arbitrary view counts and the project reports 8 GB for 1/4/8/16-view inference.
Best fit for Havenline: feed the existing front/3q/side/back sheets to the SMPLX-FREE variant and use the resulting 3D representation as a silhouette/identity oracle.

Important limitation discovered during source audit:
- the current official export is standard **3D Gaussian Splatting PLY**, not a conventional triangle mesh;
- the original LHM `inference_mesh.sh` likewise routes to `save_ply()` for Gaussian output despite the command name saying mesh.

Therefore LHM/LHM++ must not be mistaken for a direct Unity Humanoid generator. Use it to tell us what the character should look like from arbitrary angles and to catch bad AI inventions in a triangle-mesh candidate.

Acquisition gate: retain the exact license/terms for the selected pretrained checkpoint and every downloaded prior asset. Prefer the SMPLX-FREE/PixelShuffle variants and avoid introducing standard SMPL-X body-model files into the commercial production path.

### 3. Stable Fast 3D — PBR triangle-mesh comparison

Source: https://github.com/Stability-AI/stable-fast-3d
Hardware: about 6 GB VRAM.
Output: UV-unwrapped GLB with material parameters and configurable texture resolution/remeshing.
License gate: Stability AI Community License. Current limited commercial use is free only while eligibility conditions are met, with registration and notice obligations. Re-check at acquisition and release.

### 4. UniRig — final skeleton / skinning candidate

Source: https://github.com/VAST-AI-Research/UniRig
License: MIT.
Hardware: project states >=8 GB CUDA GPU for generation.
Role: cleaned triangle mesh -> predicted/refined skeleton -> skin weights -> FBX/GLB -> Unity Humanoid.

## 8K authoring texture stack

### Real-ESRGAN
Source: https://github.com/xinntao/Real-ESRGAN
License: BSD-3-Clause.
Role: controlled upscaling/restoration of the original approved source art before projection. It may recover edge clarity and reduce compression artifacts; it must not be treated as evidence that invented detail is authentic.

### Material Map Generator
Source: https://github.com/joeyballentine/Material-Map-Generator
License: Apache-2.0.
Role: candidate helper for deriving normal/displacement/roughness information from projected source textures. Every generated map must be art-reviewed because clothing, skin, fur, hair, leather and metal need different material responses.

### Blender
Role: authoritative geometry cleanup, sculpt correction, UV layout, multi-view camera projection, seam cleanup, texture baking, normal/AO baking and LOD authoring.

## What '8K character' means for Havenline

The production target is an **8192 x 8192 authoring master**, not blindly shipping 8K textures on Android.

Recommended authoring pipeline:

1. Upscale/clean each approved turnaround view non-destructively.
2. Produce candidate triangle geometry with TripoSR/SF3D.
3. Compare that geometry against the LHM++ multi-view 3D reference and the original turnaround silhouettes.
4. Correct head/body proportions, coat volume, fur, backpack, boots, hair and accessories in Blender.
5. Build final UVs.
6. Project front/3q/side/back approved art directly onto the mesh.
7. Blend seams and paint only missing transition areas; do not redesign the character.
8. Bake 8K authoring masters: base color, normal, roughness, AO/cavity and Unity mask maps.
9. Rig with UniRig + manual correction.
10. Produce runtime LOD0/LOD1/LOD2 and 4K/2K ASTC material variants.
11. The exact Havenline shipping camera decides which runtime resolution survives.

An 8192 texture setting in an AI generator alone does **not** constitute true 8K character detail.

## Human-specific tools rejected/held for R&D

### LHM / LHM++ as direct Unity mesh generator
Reason: current export is Gaussian PLY, not a standard skinned triangle character mesh. Retain as a multi-view fidelity/reference tool.

### IDOL
Source: https://github.com/yiyuzhuang/IDOL
Top-level code: MIT.
Hold reason: official setup requires downloaded SMPL-X and FLAME templates and recommends 24 GB+ VRAM. Standard SMPL-X rights are non-commercial scientific research unless separate commercial rights are acquired. It also exceeds the intended RTX 3080 production envelope.

### SiTH
Source: https://github.com/SiTH-Diffusion/SiTH
Top-level code: MIT.
Strength: textured human mesh, UV/animation-oriented output.
Hold reason: official pipeline explicitly requires SMPL-X model files. Standard SMPL-X license is not free for commercial shipping.

### PSHuman
Source: https://github.com/pengHTYX/PSHuman
Top-level code: MIT.
Strength: textured human reconstruction and a SMPL-free inference option.
Hold reason: current 768 model is documented as requiring over 40 GB VRAM; the project also borrows/reorganizes models from ECON/SIFU, so dependency rights require a deeper audit. Not suitable for the RTX 3080 production lane today.

### CharacterGen / Unique3D
Top-level licenses are permissive, but their released requirements depend on NVIDIA `nvdiffrast`. NVIDIA's current source-code license restricts that software to non-commercial research/evaluation unless separate rights are obtained. R&D comparison only.

### IDOL / SiTH / SMPL-X family
Do not confuse an MIT repository wrapper with commercial rights to required body-model data. The SMPL-X model/software license is the controlling blocker when those files are required.

## Acceptance bar

No character is promoted because it is technically rigged or has an 8K texture. It must:

- visibly be the approved Havenline character from front/3q/side/back,
- preserve premium stylized proportions rather than drifting photoreal or low-poly,
- retain the exact winter outfit color blocking and major gear,
- deform cleanly in locomotion/gather/carry/deposit/rescue/defense poses,
- look materially richer than the rejected V7/prototype characters,
- read at the same close isometric scale and polish standard as the locked Whiteout Survival reference footage.
