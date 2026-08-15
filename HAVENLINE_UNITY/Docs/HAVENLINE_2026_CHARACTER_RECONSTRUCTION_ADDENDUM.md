# HAVENLINE — 2026 Character Reconstruction Addendum

Date: 2026-08-15
Status: ACTIVE AUDIT

This addendum records newer tools discovered after the initial 2D-to-3D pipeline audit. The reference remains the locked Whiteout Survival slice and the approved Havenline multi-view character sheets.

## Strong new candidate: Pixal3D

Source: https://github.com/TencentARC/Pixal3D
Model: https://huggingface.co/TencentARC/Pixal3D
License: Pixal3D code, parameters and weights are currently published under MIT. Its NOTICE makes clear that third-party components remain under their original licenses.

Why it matters:
- SIGGRAPH 2026 image-to-3D model.
- Pixel-aligned conditioning is designed for much stronger image fidelity than loose prompt/attention conditioning.
- Generates detailed geometry and PBR attributes.
- Official inference exposes a real mesh object with `vertices` and `faces`.
- Official script exports GLB.
- Low-VRAM mode is documented as reducing peak VRAM from roughly 18GB to about 10–12GB, using 1024 resolution by default.
- This puts a 12GB RTX 3080 within the documented low-VRAM envelope and a 10GB 3080 near/below the lower boundary; the exact local card VRAM must be checked before relying on it.

### Commercial dependency caveat

Pixal3D main currently builds on TRELLIS.2. TRELLIS.2's standard installation includes NVIDIA `nvdiffrast` and `nvdiffrec`. The TRELLIS.2 `o_voxel.postprocess.to_glb()` function directly imports `nvdiffrast` and uses it during UV-space PBR texture baking.

Therefore **do not treat the stock Pixal3D textured-GLB export path as commercially cleared** merely because Pixal3D itself is MIT.

### Promising geometry-only path — requires local validation

Pixal3D's inference script obtains `mesh.vertices` and `mesh.faces` before calling `o_voxel.postprocess.to_glb()`. The top-level Pixal3D requirements do not directly include `nvdiffrast`.

Candidate Havenline approach:
1. Run Pixal3D image-to-shape inference in low-VRAM mode.
2. Stop before TRELLIS.2 `to_glb()`.
3. Export raw `vertices` + `faces` with a permissive mesh writer such as `trimesh`.
4. Perform topology cleanup, UVs and all Havenline texturing in Blender using the approved model-sheet views.
5. Never import/use restricted PBR bake/render code in the commercial asset-production step.

This path is a **source-supported hypothesis, not yet cleared**. Local dependency tracing must prove the shape-inference path can execute without loading or requiring the restricted NVIDIA components before it is used for a shipping asset.

## Extremely relevant research reference: ModelSheetTo3D

Source: https://github.com/hjyoon02/ModelSheetTo3D
Paper: Eurographics 2026, “3D Character Reconstruction from Hand-drawn Model Sheets.”

This is conceptually almost a perfect match for Havenline because the project already has front/3-quarter/side/back model sheets for Characters 1–4.

The public implementation:
- segments multiple views,
- accepts explicit yaw angles for views,
- generates an initial mesh,
- deforms/refines it against model-sheet information,
- outputs a textured mesh and stylized multi-view renderings.

However it is **not approved for Havenline production**:
- the GitHub repository currently does not declare a repository license;
- its README explicitly lists `nvdiffrast` as a dependency;
- its default base-mesh recommendation is TRELLIS, with alternatives such as TRELLIS.2 or Meshy.

Use the paper/workflow only as an R&D reference for what our own permissive multi-view fitter should accomplish. Do not copy or ship the unlicensed/restricted implementation.

## Rejected new candidate: AniGen

Source: https://github.com/VAST-AI-Research/AniGen
Strength: single image -> mesh + articulated skeleton + skin weights, directly animation-oriented.
Hardware: authors document >=18GB VRAM.

Production blocker: AniGen's own source is MIT, but its own `THIRD_PARTY_LICENSES.md` states that a bundled BVH component from NVIDIA/instant-ngp is restricted to non-commercial/research use. Therefore the released pipeline is not a clean free commercial path for Havenline.

## Proposed Havenline-native model-sheet fitting route

The best long-term solution may be to combine permissive/free components into our own reproducible fitting pipeline rather than search forever for one magic converter.

### Stage A — base triangle mesh
Use, in order:
1. Pixal3D geometry-only path if dependency-cleared locally.
2. TripoSR as the known permissive fallback.
3. Stable Fast 3D only if its current commercial-license eligibility is recorded.

### Stage B — authoritative four-view fitting
Use the approved front / 3-quarter / side / back character views as hard evidence.

In Blender, build a custom scripted fitting pass that:
- creates calibrated orthographic reference cameras for each view,
- extracts alpha/silhouette masks from the approved sheets,
- renders candidate silhouette masks,
- measures per-view silhouette error,
- optimizes or guides cage/lattice/shape-key deformation,
- separately checks head, hair, torso/coat, backpack, arms, legs and boots,
- prevents any single view from becoming correct at the expense of another.

This is especially important for Havenline's stylized proportions, large winter coats, fur cuffs, backpacks and expressive heads, which generic human-body recovery systems often normalize away.

### Stage C — 8K identity projection
The final texture identity comes from the approved art, not an AI hallucination:
- upscale/clean the original views with a permissive tool such as Real-ESRGAN,
- UV unwrap final topology,
- camera-project all authoritative views,
- resolve seams by source-weighted blending,
- derive/bake normal, roughness, AO and mask masters,
- retain 8192x8192 authoring masters,
- derive optimized ASTC runtime textures for Unity.

### Stage D — rig
Use UniRig plus manual skeleton/weight review, then Unity Humanoid and existing Havenline stress-pose gates.

## Current priority order

1. Verify exact VRAM size of the local RTX 3080.
2. Audition Character 1 with TripoSR.
3. Audition Character 1 with Pixal3D low-VRAM if the local dependency trace proves geometry generation can run without restricted components.
4. Build multi-view silhouette comparison against the existing Character 1 turnaround.
5. Keep the better geometry; correct it in Blender.
6. Reproject the approved art into an 8K master.
7. UniRig -> Unity Humanoid -> shipping-camera render.
8. Only then replicate the winning pipeline for Characters 2–4.

The acceptance target is not 'AI generated a 3D model.' The target is: the character looks like the approved Havenline art from every authoritative view and reads with the polished stylized quality of the locked Whiteout Survival gameplay reference in the actual Unity shipping camera.
