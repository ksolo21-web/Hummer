# HAVENLINE — Free Photogrammetry Scan Pipeline

Date: 2026-08-15
Status: APPROVED R&D / SOURCE-ASSET CREATION PATH
Project: Unity 6000.3.18f1 / URP 17.3.0 / Android
Visual benchmark: approved Whiteout Survival gameplay segment.

## Why this exists

The free marketplace search already supplies many high-quality assets, but a coherent AAA-like game cannot depend on finding a perfect free listing for every prop and surface. Photogrammetry gives Havenline a legal zero-cost path to create its own realistic source assets from real-world objects and surfaces, then optimize them for Unity mobile.

This pipeline is especially valuable for:
- logs, split firewood and stumps;
- weathered crates and storage bins;
- rope bundles;
- axes/tools and simple camping equipment;
- rocks/fire-pit stones;
- dirty/compacted snowbanks and snow-covered ground patches;
- canvas/tarp weathering reference;
- boots, gloves and other non-deforming accessory reference;
- small environmental clutter unique to Havenline.

Do not scan copyrighted branded objects, identifiable third-party logos, protected artworks, or other content we do not have rights to reproduce.

---

## Route A — Meshroom / AliceVision — unconditional open-source route

Official sources:
- https://alicevision.org/view/meshroom.html
- https://github.com/alicevision/Meshroom

Current version observed: Meshroom 2025.1.0.
License: MPL-2.0.

Meshroom 2025.1 unified production computer-vision workflows including:
- photogrammetry;
- object reconstruction;
- turntable object reconstruction;
- 360-degree/two-sided object workflows;
- color calibration;
- RAW conversion;
- LiDAR meshing;
- multi-view photometric stereo.

The standard photogrammetry pipeline includes CameraInit, FeatureExtraction, ImageMatching, FeatureMatching, StructureFromMotion, dense-scene preparation, DepthMap, filtering, Meshing, MeshFiltering and Texturing.

GPU note: official prebuilt 2025.1 binaries are CUDA 12 builds and are designed for NVIDIA GPUs with compute capability >= 5.0. The Havenline workstation's RTX 3080 is therefore a strong fit once this pipeline is run locally.

Decision: Meshroom is the default revenue-independent free scan pipeline.

---

## Route B — RealityCapture / RealityScan desktop — conditional $0 route

Official source:
https://www.capturingreality.com/realitycapture

Current published pricing states $0 with all RealityCapture features for individuals and small businesses that made less than $1 million USD revenue in the prior 12 months, plus educational institutions/students. Businesses above the stated threshold require the applicable paid seat/subscription.

Use only if the user/project qualifies under the terms in force at the time of use. Archive the license/pricing evidence at acquisition time.

Why audition it:
- fast photo/laser-scan reconstruction;
- textured high-density meshes;
- normal/displacement generation;
- strong high-poly-to-low-poly texture reprojection/baking workflow.

Decision: high-productivity optional path, not a licensing assumption.

---

## Capture recipe — small prop

Target examples: axe, lantern, kettle, crate, backpack shell, rope, split logs.

1. Use diffuse/soft lighting; avoid hard moving shadows.
2. Lock exposure/focus where practical.
3. Remove/avoid glossy reflections where possible; cross-polarized capture can be added later if we build a dedicated rig.
4. Capture 80–200 sharp overlapping photos around the object.
5. Maintain roughly 70–80% overlap between neighboring views.
6. Capture three height bands: low, mid and high.
7. Add dedicated top/underside passes when geometry permits.
8. Fill the frame with the object; do not waste pixels on background.
9. Include a measured scale reference outside the final usable texture area.
10. Photograph color reference/checker for hero assets if accurate albedo matters.

Do not rely on phone depth/LiDAR alone for final texture quality. High-resolution ordinary photos can produce stronger surface detail when capture is controlled.

---

## Capture recipe — ground / snow / rock patch

Target examples: snowbank, compacted snow patch, rocky drift, icy/muddy transition.

1. Shoot in overcast/diffuse conditions when possible.
2. Walk a consistent grid around/over the patch.
3. Keep high overlap and add oblique angles around edges.
4. Capture enough surrounding context to solve camera positions reliably.
5. Avoid footprints entering/changing the patch mid-capture unless the tracks are intentionally part of the asset.
6. Record physical scale.
7. Capture a second clean tile/patch for material extraction if the hero scan contains too much unique geometry.

Snow is difficult because low-texture clean white regions give photogrammetry fewer visual features. Dirty, tracked, crystalline or shadowed snow usually reconstructs more reliably. Clean powder snow should often be a PBR material plus authored displacement/geometry rather than a pure scan.

---

## Reconstruction → game asset pipeline

### Stage 1 — reconstruct source
- align cameras;
- generate dense depth/point cloud;
- reconstruct high-poly mesh;
- produce source texture/albedo;
- crop away unrelated environment;
- set real-world scale.

### Stage 2 — source cleanup
Tools: Meshroom/RealityCapture + Blender/MeshLab/UModeler X.
- remove floating scan fragments;
- fill only appropriate holes;
- preserve silhouette-critical damage and wear;
- delete unseen underside/interior geometry where appropriate;
- correct normals;
- remove logos/identifiable third-party markings.

### Stage 3 — create game mesh
- retopologize/decimate to a silhouette-driven target;
- preserve curvature on outer contour;
- aggressively simplify flat/invisible areas;
- build clean UVs;
- build simple collision separately;
- create LOD0/1/2 where the object remains visible at multiple camera distances.

### Stage 4 — bake/reproject
From source scan to runtime mesh:
- base color/albedo;
- normal;
- AO/cavity where useful;
- roughness derived/authored rather than trusting baked color highlights;
- optional height/parallax only where the Unity material actually uses it.

Avoid baking direct illumination/shadows into albedo when possible. The object needs to relight correctly under Havenline's cold daylight and warm furnace states.

### Stage 5 — Unity-ready texture packing
- 2K for hero large props if shipping-camera evidence needs it;
- 1K for most repeated props;
- lower for small clutter;
- ASTC on Android;
- pack metallic/roughness/AO/masks where the chosen URP shader layout permits;
- enable mipmap streaming on large PBR textures.

### Stage 6 — physical integration
A scan still looks pasted-in if it does not belong to the world. Add:
- snow accumulation decals/geometry;
- contact AO;
- soot/wetness/melt masks;
- footprints/drag marks;
- correct collision/contact placement;
- consistent color/roughness calibration with surrounding asset families.

---

## Mobile targets

Photogrammetry masters are not runtime meshes.

Guideline examples:
- simple axe/tool: roughly 1K–5K tris LOD0 where silhouette permits;
- crate/lantern/kettle: roughly 2K–10K;
- hero fire pit/log pile: roughly 5K–20K depending silhouette and screen size;
- ground snow/rock patch: reduce heavily and rely on baked normal/height detail;
- tree/stump scan: create aggressive LOD chain and far impostor/billboard where required.

These are starting ranges, not automatic pass/fail numbers. The exact shipping camera and device GPU timing decide final budgets.

---

## High-value first Havenline scans

If/when local capture is used, prioritize objects that are hard to find as a coherent free set:

1. Three to five distinct split logs/firewood pieces.
2. One natural stacked woodpile.
3. Weathered wooden crate/bin.
4. Rope bundle / tied rope detail.
5. Canvas/tarp folds and weathering reference.
6. Rock/firepit cluster.
7. Snow/ice/dirty-ground transition patch.
8. Tool/axe handle wear closeups for texture reference.
9. Boot tread/footprint reference for snow decals.
10. Small lived-in camp clutter that gives Havenline its own identity instead of a marketplace-kit identity.

---

## Quality gate

A photogrammetry asset is not approved because it is a scan.

Reject or redo if:
- lumpy/noisy geometry is visible in silhouette;
- albedo contains baked lighting that conflicts with Havenline lighting;
- scan texture resolution is wasted on invisible areas;
- decimation destroys recognizable shape;
- runtime mesh/material count is excessive;
- asset looks photographic while neighboring assets remain flat/stylized;
- it performs poorly on the Android target;
- it does not materially improve the actual Havenline shipping-camera frames.

## Outcome

This pipeline removes the dependency on finding a perfect free asset for every category. Marketplace CC0/free assets remain the fastest path for common objects, while Meshroom/RealityCapture lets Havenline author unique scan-quality assets where the free library has gaps.