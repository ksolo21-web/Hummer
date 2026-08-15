# HAVENLINE — AAA Free Asset Research Wave 3

Date: 2026-08-15
Visual benchmark: approved Whiteout Survival gameplay segment.
Status: CONFIRMED NEW FINDS / AUDITION QUEUE

## New confirmed free snow content

### Photogrammetry Snow Material Pack
Source: https://www.fab.com/listings/79e7ea2c-43be-4ba1-b236-757fd06440c9
Observed: Free
Content: six unique 4K PBR snow materials made from photogrammetry; compacted, soft and natural surface variations; tileable and advertised for Unity/game-engine use.
Use: replace one-note snow with coherent compacted/soft/trampled variants.
Production rule: convert source textures to Havenline URP map packing; use only the variants that read differently at the shipping camera.

### Alaskan Cliff Rock 1 Free
Source: https://www.fab.com/listings/3f17a56c-8c6a-440c-97c9-4a9cd156732f
Observed: Free
Source: photogrammetry; 2K albedo/roughness/normal; around one million vertices.
Use: northern/frozen forest perimeter and cliff silhouette source.
Production rule: source master only. Decimate, bake and LOD.

### Alaskan Cliff Rock 3 Free
Source: https://www.fab.com/listings/b8c4545b-ab17-4a96-ae8b-aee90e4ee3cd
Observed: Free
Source: photogrammetry; 2K albedo/roughness/normal; around one million vertices.
Use: second northern rock shape for repetition control.
Production rule: source master only. Decimate, bake and LOD.

### Free Rock Pile Scan
Source: https://www.fab.com/listings/5f6cb2fc-3973-492d-8e1c-60ba845f57b3
Observed: Free
Description: medium-poly photogrammetry rock-pile scan.
Use: natural grouping around camp perimeter/fire zones.

### Mossy Rock — Free Photogrammetry 3D Asset
Source: https://www.fab.com/listings/2fb865a9-2329-42e4-a495-ff003397c584
Observed: Free
Listing explicitly states free use in personal and commercial projects, 2 LODs, PBR maps, and Unity compatibility.
Use: source for rock topology/detail; winterize with snow/frost material blending rather than using green moss unchanged.

---

## New confirmed free camp-life props

### Camping Cooking Pots
Source: https://www.fab.com/listings/b1c02413-796c-4a1e-84d0-a4070f93ca3d
Observed: Free
Content:
- large pot;
- small pot;
- kettle;
- pan.
Technical:
- 12,952 triangles total;
- 9,882 vertices;
- non-overlapping UVs;
- 2K textures per item;
- PBR metallic/roughness workflow;
- packed AO/Roughness/Metallic map included;
- FBX/OBJ/GLB/glTF/USDZ.
Use: furnace/cooking zone, shelter interior, storage clutter.
Why: realistic medium-detail camp equipment that reads as functional rather than decorative primitives.

### Free 4K Wood Log Scan
Source: https://www.fab.com/listings/95e51106-ecb1-48e7-9f2b-3b52687cd718
Observed: Free
Source: photogrammetry with 4K Base Color/Normal/Roughness/AO.
Use: individual carried wood/log donor; combine several rotated/scaled/baked variants into resource bundles.

### Forest Stones Campfire
Source: https://www.fab.com/listings/7393be9b-e10e-4363-82d4-191666f3aa98
Observed: Free
Source: 120 x 36MP photogrammetry scan, two 8K textures plus high-detail normals.
Use: high-quality firepit geometry source.
Production rule: bake to mobile mesh and pair with CC0 flipbook fire/smoke.

---

## New confirmed free lightweight VFX path

### Unity Labs CC0 fire/smoke/explosion flipbooks
Official source: https://unity.com/blog/engine-platform/free-vfx-image-sequences-flipbooks
License: CC0
Unity explicitly released the image sequences/assembled flipbook sheets for unrestricted project use.
Use: furnace flame, campfire flame, smoke puffs/loops and embers without realtime fluid simulation.
Mobile value: pre-simulated flipbooks are a much better baseline than expensive volumetric fire/smoke for an Android isometric game.

### Brackeys VFX Bundle
Source: https://brackeysgames.itch.io/brackeys-vfx-bundle
License: CC0
Use: secondary particle/flipbook source where visual style can be made realistic enough.
Constraint: only use effects that match Havenline's grounded look.

### CGHEVEN CC0 VFX library — secondary source
Source: https://cgheven.com/assets
Claimed license on current site: CC0 for its free VFX/3D/flipbook/HDRI library.
Use: audition realistic campfire/smoke flipbooks where Unity Labs content lacks the exact profile.
Rule: retain a copy/screenshot of the license page at asset acquisition because this is a third-party library rather than Unity's own release.

---

## New free LOD / geometry tooling

### UnityMeshSimplifier
Source: https://github.com/Whinarn/UnityMeshSimplifier
License: MIT
Latest release observed: v3.1.1, January 7, 2026.
Features:
- C# mesh simplification;
- Editor/runtime support;
- LOD Generator API/helper;
- smart vertex linking to reduce simplification holes/artifacts.
Use: automate first-pass LOD generation for photogrammetry props, rocks, camp clutter and some hard-surface assets.
Rule: generated LODs still require visual inspection; tree foliage and skinned characters may need specialized/manual handling.

### Fast Terrain To Mesh Converter
Source: https://github.com/roundyyy/Fast-Terrain-To-Mesh-Coverter
License: MIT
Repo states Unity 6, URP, VR/mobile use; supports terrain-to-mesh, vegetation export, tree LOD preservation, texture arrays and height-based blending.
Use: R&D option if converting compact Havenline terrain to a more controlled mobile mesh becomes beneficial.
Do not adopt without profiling against Unity Terrain first.

---

## Deliberate non-adoption — Unity Virtual Mesh

Official repository: https://github.com/Unity-Technologies/com.unity.virtualmesh
This is technically exciting: GPU-driven static-object virtual geometry, triangle-cluster LODs and occlusion culling. But current repository notes still list platform/support/UX/performance work, and it is Vulkan/RenderGraph-focused.

Decision: WATCHLIST ONLY. Havenline cannot make an experimental virtual-geometry package a dependency for the baseline Android build while the existing project is still stabilizing. Use conventional authored LODGroups, optimized meshes, SRP batching and Vulkan enhancements first.

---

## Whiteout-style camp dressing rule

The target is not “more props.” Each prop must explain survival activity:
- pot/kettle near heat/cooking;
- split logs between wood source, carried bundle, storage and furnace;
- sleeping bag inside/adjacent to shelters;
- rope at tent anchors/repairs;
- crates around resource storage;
- tracks from repeated survivor paths;
- soot and melt where heat changes the snow.

This causal placement is what makes mixed free assets read as one authored environment instead of an asset-store collage.

## Next research gaps

Continue searching for:
1. higher-quality fully free winter shelter/tent with strong close-camera canvas geometry;
2. realistic free civilian scarves/insulated pants/boots that fit the survivor stack;
3. a stronger commercial-usable free wolf with fur-card/realistic material treatment;
4. free snow-covered branch/deadwood scans;
5. free food/canister/thermos/jerry-can assets with explicit zero-price confirmation;
6. lightweight Unity 6-compatible decal/snow-contact solutions;
7. high-quality free ambient winter audio and isolated furnace/mechanical loops.
