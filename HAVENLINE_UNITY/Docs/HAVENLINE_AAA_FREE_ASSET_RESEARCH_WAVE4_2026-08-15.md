# HAVENLINE — AAA Free Asset Research Wave 4

Date: 2026-08-15
Status: CONFIRMED FREE WINTER-FOREST DETAIL SOURCES
Primary benchmark: approved Whiteout Survival gameplay segment.

## Purpose

The procedural scene currently fails not only because the hero assets are simple, but because the ground/forest edge has almost no believable small-scale natural history. This wave targets free scan-quality deadwood, trail and branch content that can make the frozen camp feel embedded in a real forest rather than placed on a white plane.

## Dead Tree — winter RealityScan
Source: https://www.fab.com/listings/533ff4e1-12e2-471b-ab54-cf45d693712f
Observed: Free
Description: cut/dead wood captured in winter scenery using RealityScan.
Format: OBJ.
Use: logged-out tree/stump/deadwood variation near the resource-gathering zone.
Production action: clean/decimate/bake and add Havenline snow-contact treatment.

## Forest Trail — winter photogrammetry
Source: https://www.fab.com/listings/f668d69e-95fe-4d1e-bcfb-f1ba18a30ac6
Observed: Free
Format: FBX + converted glTF/GLB/USDZ.
Description: photogrammetry of a snowy forest trail.
Use: reference/source for compacted path shape, snow-edge breakup and real traveled-ground composition.
Production action: do not transplant a huge unique scan as the whole level; extract/bake useful surface/shape ideas into compact Havenline path modules and decals.

## Quixel Fallen Branches
Source: https://www.fab.com/listings/874d948f-b6a2-4846-bdeb-49840d70658b
Observed: Free
Type: decal / texture-set source.
Scan density: 8192 px/m.
Maps: Normal, AO, Bump, Specular, Opacity, Roughness, Cavity, Gloss, Base Color.
Use: forest-floor twig/branch clusters under pine/fir trees and around snowbank edges.
Production action: atlas into shared forest-floor decal/material set to avoid unique draw/material overhead.

## Dead Pine Twigs 02
Source: https://www.fab.com/listings/6fc1aead-0f6d-4d37-baf7-076eaa373907
Observed: Free
Capture: photometric stereo atlas with cross-polarized/calibrated albedo and crisp normal detail.
Maps: supplied PBR variants for metallic/roughness and specular/gloss workflows; OpenGL and DirectX normals.
Use: realistic conifer litter/branch atlas and optional low-cost card geometry.
Production action: convert to Havenline URP atlas; use sparse cards/decals rather than dense geometry.

## Broken Tree Branch FREE
Source: https://www.fab.com/listings/2cb4839b-9bc6-4225-bece-98aea577242f
Observed: Free
Capture: Canon R7 / 24mm camera scan.
Content: optimized/smoothed low-poly OBJ, 1K Base Color/Roughness/Normal.
Use: ready-made low-cost deadwood ground prop, useful because it already starts closer to mobile runtime density than raw scans.

## Wood Branch
Source: https://www.fab.com/listings/885de738-9687-49bf-80ec-06b4d93c6d52
Observed: Free
Type: photogrammetry wood branch.
Format: FBX + glTF/GLB/USDZ.
Use: additional unique fallen-branch silhouette.

## Firewood 1 Photoscan LOWPOLY
Source: https://www.fab.com/listings/2c7d687d-3a3a-43ec-9b9d-73b274dada57
Observed: Free
Capture: Polycam scan, repaired/retopologized in Blender.
Textures: 1K color + AO.
Use: low-cost background/resource firewood prop where higher-detail 4K/8K scans would be wasteful.

## Old Decayed Wood
Source: https://www.fab.com/listings/91640eaa-7973-469b-9ee0-64689b2d1065
Observed: Free
Description: weathered wood found in snowy grass; raw photogrammetry model.
Use: weathered material/shape source for forest edge and fuel-storytelling variation.
Production action: raw source only; must be cleaned and LOD-authored before use.

## Character accessory note

### Cloth - Wrap Hoodie
Source: https://www.fab.com/listings/0e6935a6-d59a-4059-a252-88a7de13d929
Observed: Free
Content: three neck-scarf/cloth-wrap/hood variants, intentionally high-topology source models suitable for baking/testing.
Use: cold-weather scarf/hood silhouette donor for Havenline survivors.
Rule: do not ship the high-topology source; retopologize/bake and skin to final survivor rig.

## Composition rule

Use this content to form authored environmental gradients:

Camp core:
- mostly compacted/dirty snow;
- logs, wood chips, footprints, soot, supplies.

Camp edge:
- loose snow clumps;
- isolated branches/deadwood;
- pine debris becomes more visible.

Forest edge:
- fallen-branch/twig decals;
- roots/stumps/dead trees;
- irregular rocks and snowbanks;
- denser saplings/trees.

Deep background:
- large optimized tree silhouettes and snow-covered rock formations;
- fewer small props because they no longer contribute meaningful screen-space detail.

This gradient is more important than randomly scattering a large number of free props.