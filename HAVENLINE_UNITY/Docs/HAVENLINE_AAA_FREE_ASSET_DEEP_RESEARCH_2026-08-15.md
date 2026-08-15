# HAVENLINE — AAA Free Asset Deep Research

Date: 2026-08-15
Status: ACTIVE RESEARCH / ACQUISITION QUEUE
Primary benchmark: approved Whiteout Survival YouTube gameplay segment — close isometric frozen-survival presentation, furnace-centered camp, readable survivors, dense environment, polished animation/VFX.

## Rule

This research exists to replace the procedural/block-built prototype art, not decorate it. Only assets with a real chance of improving the shipping camera are promoted. Low-poly/voxel/stylized packs that preserve the rejected blocky language are excluded even when free.

Every external asset must retain source URL, observed license/price, acquisition date, and original archive checksum before promotion to production.

---

## TIER S — Photoreal environment foundation

### Quixel Megascans on Fab — selected listings currently observed as Free
Fab Standard License permits commercial/private use, modification, commercial distribution as part of a project, and use in compatible tools beyond Unreal. Acquire the actual Fab entitlement and retain proof; do not rely on a legacy Quixel/Bridge entitlement with different terms.

#### Snow material
Source: https://www.fab.com/listings/2ddf31c1-5d66-49b9-ac6a-284ebf57c9e6
Observed: Free
Formats: texture set / glTF
Maps: base color, normal, roughness, AO, cavity, bump, gloss, specular, displacement on applicable tier
Use: primary clean/powder snow master material.

#### Snow Pile
Source: https://www.fab.com/listings/8e55612b-1f02-4262-aad4-f1a00dfa7c62
Observed: Free
Formats: FBX, glTF/GLB/USDZ conversions
Physical scale: about 6.46 x 7.15 x 2.08 m
Use: break the current flat snow plane with real scanned banks and drift silhouettes.

#### Rocky Snow Pile
Source: https://www.fab.com/listings/59841190-6e9e-449b-bb9e-61c9bcd85269
Observed: Free
Formats: FBX/glTF family
Use: perimeter/forest-edge transition, natural elevation breakup, occlusion around camp.

#### Rocky Snow Pile — alternate scan
Source: https://www.fab.com/listings/0c6bddcc-8949-4e2d-bb42-0fb27c00121d
Observed: Free
Use: variation to avoid repeated snow-bank silhouettes.

#### Snow Clump
Source: https://www.fab.com/listings/7671b700-ce1a-4b49-9d1b-3add4a6a33c3
Observed: Free
Physical scale: about 0.31 x 0.27 x 0.14 m
Use: small accumulation around tent bases, furnace feet, crates, logs, barricades and tracks.

#### Ice Cliff
Source: https://www.fab.com/listings/3697046b-9768-4bb6-a117-4dbc8a67eb61
Observed: Free
Formats: FBX/glTF family
Use: distant/perimeter frozen-rock breakup and environmental depth; not a central gameplay prop.

#### Ice Cliff — alternate scan
Source: https://www.fab.com/listings/198d6561-73ca-4d4e-adc5-efe457f5c793
Observed: Free
Use: silhouette variation.

#### Pine Debris decal/material
Source: https://www.fab.com/listings/a5638c23-799a-415f-8c4a-1a3f52f632ce
Observed: Free
Texel density listed: 8192 px/m
Use: needles, cones, bark fragments and forest-floor storytelling around trees; major antidote to the sterile white floor.

### Poly Haven — CC0 master library
All assets below are CC0. Use high-resolution masters for authoring, then create optimized Unity LODs and 1K/2K shipping texture variants where visual tests allow.

#### Pine Tree 01
Source: https://polyhaven.com/a/pine_tree_01
License: CC0
Master complexity: ~17M tris; 8K source textures
Use: source-quality conifer silhouette. MUST be decimated/LOD-authored before Android use.

#### Fir Tree 01
Source: https://polyhaven.com/a/fir_tree_01
License: CC0
Master complexity: ~8M tris
Use: second conifer species to eliminate repeated procedural-tree look.

#### Fir Sapling
Source: https://polyhaven.com/a/fir_sapling
License: CC0
Master complexity: ~433K tris
Use: foreground/midground vegetation age variation.

#### Fir Sapling Medium
Source: https://polyhaven.com/a/fir_sapling_medium
License: CC0
Master complexity: ~2M tris
Use: mid-height forest layering.

#### Pine Sapling Small
Source: https://polyhaven.com/a/pine_sapling_small
License: CC0
Master complexity: ~398K tris
Use: near-camp understory and silhouette diversity.

#### Tree Stump 01
Source: https://polyhaven.com/a/tree_stump_01
License: CC0
Master complexity: ~41K tris
Use: realistic logged-out survival-zone storytelling.

#### Dead Tree Trunk
Source: https://polyhaven.com/a/dead_tree_trunk
License: CC0
Master complexity: ~102K tris
Use: firewood source, fallen log, forest-edge storytelling.

#### Pine Roots
Source: https://polyhaven.com/a/pine_roots
License: CC0
Master complexity: ~163K tris
Use: ground-cover breakup around trees and exposed terrain.

#### Snow 01
Source: https://polyhaven.com/a/snow_01
License: CC0
Use: rough powder snow with footprint/trail character.

#### Snow 02
Source: https://polyhaven.com/a/snow_02
License: CC0
Use: softer powder/frost variant.

#### Snow 03
Source: https://polyhaven.com/a/snow_03
License: CC0
Use: trampled/muddy snow variation around active camp zones.

#### Snow 04 / Snow 05
Sources:
- https://polyhaven.com/a/snow_04
- https://polyhaven.com/a/snow_05
License: CC0
Use: dirty/muddy transition materials for high-traffic areas.

#### Snow Floor
Source: https://polyhaven.com/a/snow_floor
License: CC0
Use: compacted rough snow; useful beneath camp infrastructure.

### Poly Haven winter HDRIs — CC0 lighting/reference
Use as lighting-reference probes and optional sky/reflection sources; mobile shipping may use downsampled/cubemap-derived versions rather than full source HDRIs.

- Snowy Forest: https://polyhaven.com/a/snowy_forest — soft cool overcast forest light.
- Snowy Field: https://polyhaven.com/a/snowy_field — crisp winter sun / long shadows.
- Snow Field: https://polyhaven.com/a/snow_field — overcast low-contrast snow lighting.
- Passendorf Snow: https://polyhaven.com/a/passendorf_snow — bright low winter sun.
- Snowy Forest Path 01: https://polyhaven.com/a/snowy_forest_path_01 — cool morning forest path.
- Snowy Forest Path 02: https://polyhaven.com/a/snowy_forest_path_02 — alternate snowy forest lighting.
- Snowy Hillside: https://polyhaven.com/a/snowy_hillside — partly cloudy high-contrast winter light.
- Horn-koppe Snow: https://polyhaven.com/a/horn-koppe_snow — crisp snow/forest sunlight.

Recommended Havenline lighting audition: Snowy Forest for neutral/day readability, plus a controlled authored sunset/night state around the furnace rather than relying on a bright blue sky.

---

## TIER S — Furnace / industrial camp detail

### Poly Haven Barrel Stove
Source: https://polyhaven.com/a/barrel_stove
License: CC0
Master: ~11K tris, 8K source textures
Use: extremely strong furnace/stove alternative or secondary heater. Weathered metal/burn marks already solve much of the current toy-like furnace problem.
Status: HIGH-PRIORITY UNITY AUDITION.

### Poly Haven Barrel 03
Source: https://polyhaven.com/a/barrel_03
License: CC0
Master: ~1K tris, 4K texture source
Use: low-cost industrial clutter/storage/fuel storytelling.

### Poly Haven Barrel 02
Source: https://polyhaven.com/a/Barrel_02
License: CC0
Use: plastic water/storage drum.

### Poly Haven Barrel 01
Source: https://polyhaven.com/a/Barrel_01
License: CC0
Use: weathered industrial barrel. Avoid inappropriate hazard markings unless retextured.

---

## TIER A — Character pipeline

### Arberry — Survival Character FREE
Source: https://www.fab.com/listings/11d20d01-b764-4936-8163-cb20d05c369e
Observed: Free
Format: FBX
Use: primary male survivor audition.
Reason: modular jacket/jeans/shoes/gloves/backpack and realistic PBR treatment.

### PKO Studio — Arctic Military Soldier
Source: https://www.fab.com/listings/2c938445-441c-457a-80ed-ea4a409dc965
Observed: Free
Format: FBX/OBJ
Use: cold-weather NPC base after removing overt tactical identity.

### CG StudioX — Female Leather Jacket Free Model
Source: https://www.fab.com/listings/d541ccae-385a-4f98-b678-ea57a60c5f85
Observed: Free
Format: FBX + PBR 4K maps
Use: modular clothing source/reference for a female civilian survivor pipeline. This is clothing, not a complete shipping character.

### Female winter character watchlist — QUALITY FOUND, FREE STATUS NOT CONFIRMED
Do NOT acquire as paid content without a separate decision. These are retained because their technical quality is much closer to target and may become free/promotional or provide a visual benchmark.

- Urban Student Girl — puffer jacket, jeans, knit hat, backpack, 25 modular pieces, 6 skins, URP/HDRP/Built-in, ~77K clothed tris, facial rig and animation set: https://www.fab.com/listings/8abaa80f-0819-453c-a988-4d1f61b22614
- Winter Delivery Girl — 30 modular pieces, ~55K clothed tris, ARKit blend shapes, pickup/put-down/walk/run/fall animation set, Unity support: https://www.fab.com/listings/a0e8792b-8622-4852-9764-9b92e962927f
- Iris realistic game-ready female — rigged, 4K PBR, Unity HDRP package: https://www.fab.com/listings/7ba23275-2163-4a7f-b554-c073cf35720b
- Woman in winter coat — rigged winter clothing silhouette: https://www.fab.com/listings/f4e94f77-2504-4fde-baf0-f7ac0930be7f

Do not label any watchlist item free until Fab explicitly exposes an actual zero price / entitlement.

---

## TIER S — Animation without buying a mocap pack

### Adobe Mixamo
Source: https://helpx.adobe.com/creative-cloud/faq/mixamo-faq.html
Cost: Free with Adobe ID
Commercial usage: Adobe FAQ explicitly permits royalty-free use of Mixamo characters and animations in commercial video games.
Use: locomotion, turns, pickup, carry, work/gathering, reactions, injury/death base clips.
Constraint: Havenline must retarget, blend, clean feet and tune timing; raw Mixamo motion is source material, not final polish.

### Rokoko Studio / Rokoko Create Starter
Sources:
- https://www.rokoko.com/pricing
- https://create.rokoko.com/
Cost: Starter plan free
Free capabilities observed: record/export mocap, FBX export, cleanup filters; limited monthly AI video processing and text-to-motion imports.
Commercial usage: Rokoko Create states generated motion data may be used commercially.
Use: capture Havenline-specific actions that generic libraries do not nail — feeding furnace, chopping/gathering, carrying bundles, depositing supplies, barricade repair, warming hands, rescue interactions.
Why important: this lets us create bespoke motion for the Whiteout-like loop instead of forcing generic clips.

---

## TIER S — Professional audio at zero asset cost

### Sonniss #GameAudioGDC bundles
License: royalty-free commercial usage; no attribution required under bundle license; source sounds cannot be resold standalone.
License: https://sonniss.com/gdc-bundle-license/
Archive: https://sonniss.com/gameaudiogdc/
2026 bundle observed: 7.47GB+, 347+ files, free, royalty-free, commercially usable.
Use search targets inside bundles: winter wind, snow movement, cloth, footsteps, wood impacts/chops, metal stove/door impacts, fire, steam, wolves/animals where present, UI/subtle impacts.

### OpenGameArt CC0 — fallback/supplement
Walking on snow: https://opengameart.org/content/walking-on-snow-sound
Wind: https://opengameart.org/content/wind
Use only where quality survives audition; Sonniss is preferred for hero sounds.

---

## TIER A — Additional CC0 PBR libraries

### 3DTextures.me
License: CC0 for site textures
License/info: https://3dtextures.me/about/
Recent snow example: https://3dtextures.me/2026/05/21/snow-005-free-seamless-pbr-texture/
Use: secondary snow/slush/material blends when Poly Haven or Megascans lacks the exact transition needed.
Note: free downloads may be lower resolution than supporter versions; judge by camera scale.

### ShareTextures
Source: https://www.sharetextures.com/about
License: assets downloaded on the site are stated as CC0; 1600+ free textures, 250+ 3D models, 50+ atlases listed.
Use: weathered fabric, wood, metal, surface imperfections, atlases, secondary props.

### cgbookcase
License: CC0/public-domain PBR library.
Use: additional roughness/normal/height-rich materials for camp surfaces and imperfections.

---

## TIER A — VFX / snow interaction tools

### Unity Visual Effect Graph Samples
Source: https://github.com/Unity-Technologies/VisualEffectGraph-Samples
Use: reference implementations for higher-end smoke/particle behavior.
Constraint: samples span pipeline/version targets and are not automatically mobile-safe; extract techniques, do not import blindly.

### Existing Havenline open-source snow deformation candidate
Source: https://github.com/TheodorKnab/Sand-Shader-Unity-URP-for-Mobile
License: MIT (verify license file again at acquisition)
Use: dynamic contact/depression/track prototype adapted to snow.

### Strategy
Use geometry/material scans for static snow quality and a small dynamic interaction layer for footprints/tracks. Do not attempt expensive full-scene volumetric/displacement simulation on Android.

---

## Mobile AAA adaptation rules

AAA-looking does not mean shipping the master assets raw.

1. Keep 4K/8K/16K masters outside runtime import where practical.
2. Hero characters: target LOD0 only where camera warrants it, with aggressive LOD1/2 reductions for normal isometric play.
3. Trees: Poly Haven 8–17M-triangle masters are source assets only; build low/medium LODs and impostor/billboard far states.
4. Textures: 2K for hero/large nearby objects; 1K or lower for repeated props/foliage as proof permits.
5. Use texture arrays/atlases and shared materials for repeated camp props.
6. Bake small prop detail into normals/AO instead of retaining scan geometry.
7. Prefer baked/efficient lighting and restrained local realtime lights; furnace glow should illuminate, not become a giant orange emissive blob.
8. Use reflection probes and calibrated roughness/metallic response so steel, canvas, snow, wood and skin stop sharing the same flat material language.
9. Use decals/vertex blending for soot, dirt, compacted snow, pine debris and wetness rather than unique high-resolution materials everywhere.
10. The exact shipping camera is the final judge. Assets that look excellent in marketplace closeups but collapse at Havenline's isometric scale are rejected.

---

## Highest-value next acquisition batch

1. Quixel Snow material + Snow Pile + Snow Clump + Pine Debris.
2. Poly Haven Barrel Stove.
3. Poly Haven Pine Tree 01 + Fir Tree 01 + one sapling + Tree Stump 01 + Dead Tree Trunk.
4. Poly Haven Snow 01/02/03 material variants.
5. Arberry Survival Character FREE.
6. Female Leather Jacket free modular source while continuing full female survivor search.
7. Mixamo core locomotion/interactions.
8. Rokoko bespoke furnace/gather/carry/deposit motion tests.
9. Sonniss GDC 2026 and prior bundle selective audio pull.
10. Winter HDRI lighting auditions for physically believable cool fill and shadow color.

## Success condition

Do not call this art replacement successful because assets imported or tests pass. Success requires actual Unity shipping-camera frames to stop reading as procedural/blocky/prototype and instead reach the polished, dense, readable frozen-survival presentation established by the Whiteout Survival reference clip.