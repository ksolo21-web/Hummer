# HAVENLINE — Free AAA Rendering + Authoring Pipeline

Date: 2026-08-15
Project target verified: Unity 6000.3.18f1 + URP 17.3.0
Visual benchmark: approved Whiteout Survival gameplay segment.

## Goal

Use free high-fidelity source assets plus current Unity 6 rendering/animation systems to replace the procedural/block-built prototype with a dense, polished frozen-survival presentation. The target is not “marketplace screenshot quality”; the target is the actual Havenline shipping camera on phone/tablet/fold/night proof views.

---

## 1. Free mesh cleanup / optimization / asset adaptation

### UModeler X — FREE, commercial use permitted
Source: https://assetstore.unity.com/umodeler-x
Observed official FAQ:
- Free edition is functionally identical to core UModeler X feature set, with anonymized telemetry.
- Commercial use is explicitly permitted with no royalties/per-seat fees on assets created.
- Supports Unity 2022.3+ including Unity 6 and Built-in/URP/HDRP.
- Editor-only authoring tool.

Havenline uses:
- clean/repair imported scans;
- remove unseen scan geometry;
- create mobile LOD variants;
- adjust tent/shelter proportions;
- fit/rework donor props;
- UV cleanup and localized mesh edits without round-tripping every adjustment out of Unity.

### Blender — free/open-source primary DCC
Use for:
- retopology/decimation;
- joining modular clothing;
- weight transfer / skinning fixes;
- texture baking from high-poly scans to mobile meshes;
- corrective shape keys;
- tree LOD generation;
- custom Havenline shelter/furnace adaptation.

GPL applies to Blender itself, not artwork created with it.

### MeshLab — GPL open-source scan cleanup utility
Source: https://www.meshlab.net/
Use for batch simplification/scan cleanup where useful. Output art remains project art; do not redistribute MeshLab itself as part of Havenline.

---

## 2. Free PBR material authoring

### Material Maker — MIT/open source
Source: https://www.materialmaker.org/
Source repo: https://github.com/RodZill4/material-maker
Current release observed in 2026: 1.6.
Features relevant to Havenline:
- 200+ procedural nodes;
- Unity-compatible PBR export;
- 3D texture-painting workflow;
- community library with CC0/CC-BY/CC-BY-SA license filters.

Havenline uses:
- convert generic free clothing into coherent Havenline winter fabric;
- create soot/wetness/ice/frost masks;
- generate packed roughness/metal/AO maps;
- author seamless canvas, worn paint, cold metal and dirty snow blends;
- remove the current flat-color material language.

### ArmorPaint — open-source source build / optional
Source: https://armorpaint.org/
Use only if the team chooses to build the open-source version or obtain the inexpensive binary. Not required for the zero-cost baseline because Material Maker + Blender cover the necessary PBR workflow.

---

## 3. Free humanoid rigging + retargeting

### Reallusion AccuRIG — FREE for personal and commercial use
Source: https://actorcore.reallusion.com/static-page/auto-rig/pre_page/accurig/accurig.html
Official FAQ confirms commercial use of AccuRIG software; each input model still follows its own asset license.
Use:
- rig free realistic survivor bodies after clothing/body edits;
- establish clean Humanoid-compatible skeletons;
- re-rig retopologized variants when marketplace rigs are unusable.

### Mixamo — free animation/autorigger source
Primary large free locomotion/action source; use for base clips, then clean and retarget.

### Unity Animation Rigging 1.4.0 — official Unity package
Package: com.unity.animation.rigging
Official Unity 6 package release: 1.4.0.
Use after base mocap:
- Two-Bone IK for planted feet;
- hand constraints for axes, logs, crates and backpacks;
- hand-to-furnace/warmth interaction;
- look/head constraints;
- carrying offsets per body size;
- correction layers so interactions line up with actual world props.

Do not replace the animation source with procedural IK; use IK to make high-quality mocap contact the world correctly.

### Rokoko free Starter/Create workflow
Use for custom furnace-feed, chopping, carry/deposit, barricade repair, rescue and warmth animations that generic libraries do not cover convincingly.

---

## 4. Terrain authoring without buying Gaia/Terrain tools

### Vegetation Spawner | FREE — Staggart Creations
Source: https://assetstore.unity.com/packages/tools/terrain/vegetation-spawner-free-automatic-tree-grass-placement-177192
Observed current compatibility: Unity 6000.0.28, Built-in/URP/HDRP compatible.
Use:
- natural rule-based placement by height/slope;
- build forest density bands around the camp;
- mix pine/fir/sapling/stump variants without hand-placing hundreds of instances.

### Procedural Terrain Painter | FREE — Staggart Creations
Source: https://assetstore.unity.com/packages/tools/terrain/procedural-terrain-painter-free-automatic-terrain-texturing-188357
Observed: free, latest 1.0.5 (2025-06-06).
Important constraint: documentation reports OpenGL/Vulkan graphics API incompatibility.
Decision: LOCAL WINDOWS/D3D AUTHORING ONLY. Do not require this tool in Havenline CI. Use it to author splatmaps/masks, then commit resulting Terrain data.

Havenline terrain rules:
- clean powder snow outside traveled space;
- compacted snow around the furnace/camp core;
- dirty/trampled snow on paths;
- pine-debris/soil transitions at forest edges;
- subtle ice/wetness around heat/melt areas.

### StampIT! Collection — FREE heightmaps
Current Unity Asset Store free list includes a Unity 6 heightmap/stamp collection.
Use only if a stamp materially improves distant terrain silhouette. Havenline’s compact gameplay area should remain deliberately authored rather than turned into a generic open world.

---

## 5. Scanned shelter material upgrade

### Quixel Megascans Tarp — confirmed FREE listings
Examples:
- https://www.fab.com/listings/98459b1b-4c29-40ac-9bca-f44fc32b5321
- https://www.fab.com/listings/5f9638e8-46c4-46c2-883c-de3020b383bb
Observed maps include base color, normal, roughness, AO/cavity, bump, specular/gloss and displacement tiers, with ~4096 px/m scan density.

Use:
- re-material the free tent/shelter meshes with actual scanned weathered waterproof fabric;
- blend frost/snow/dirt decals over top;
- add seams/edge piping/guy ropes as geometry;
- use entrance depth and overlapping flap geometry instead of a simple white shell.

### Quixel support props — confirmed FREE
- Tangled Rope: https://www.fab.com/listings/1c93b9d5-611f-42af-84c0-fb453a70811c
- Small Rope Spool: https://www.fab.com/listings/4b571804-1632-42f3-9fc4-f001b2bb18e6
- Tarped Crate: https://www.fab.com/listings/8aa1f000-e6b2-48dc-b33d-e233fdf59956
- Burnt Firewood Pack: https://www.fab.com/listings/06a51e44-9f42-4f57-8c07-b06dfdc34d6c
- Campfire scan: https://www.fab.com/listings/7e84e0a2-e57a-4421-a127-ed747846480c
- Military Trenches Detail Sandbag Canvas material: https://www.fab.com/listings/b57f711b-8745-463b-8c4e-43dd313dc3ff
- Old Wooden Pole: https://www.fab.com/listings/8980415b-6145-4a89-84e3-29ff404bf0a8

Use selectively and de-militarize where necessary. These scans should supply believable material/shape detail, not turn the camp into a battlefield.

---

## 6. Free cold-weather character detail donors

### CC-BY game-ready boots
Source: https://sketchfab.com/3d-models/game-ready-boots-e17df79947b949a092b84eea18904b58
Observed: ~5.6K tris, game-ready, CC Attribution.
Use: survivor boot donor; recolor/retexture for coherent palette.

### CC-BY gloves
Source: https://sketchfab.com/3d-models/gloves-82850a5168604536884ddfb4330a121f
Observed: ~8.6K tris, 2K PBR, game-ready, CC Attribution.
Use: work/glove donor after fit/skin.

### CC-BY knit beanie
Source: https://sketchfab.com/3d-models/beanie-c9a7a7dcebf14666ad9505d0ceee3221
Observed: ~8.2K tris, CC Attribution.
Use: civilian winter silhouette and character differentiation.

### Free high-topo wrap/hood source
Source: https://www.fab.com/listings/0e6935a6-d59a-4059-a252-88a7de13d929
Observed: Free; three wrap/hood/neck-scarf variants intended as high-topology/bake source.
Use: bake/retopologize; never ship source topology blindly.

---

## 7. Lighting that makes PBR assets read correctly

### Adaptive Probe Volumes (APV) — built into URP 17
Unity 6 URP APV provides per-pixel probe sampling and automated probe placement, with streaming and lighting scenario support.
Havenline use:
- baked cool ambient/indirect light across the snowy camp;
- survivors and moving props pick up believable environment lighting;
- reduce the black-silhouette character problem without flattening the image;
- higher probe density in the furnace/shelter core, lower density in empty perimeter snow.

Target: one tightly scoped camp APV bake, not an oversized open-world probe volume.

### Reflection Probes — built into URP
Use a small number of carefully placed baked probes to make:
- metal furnace/stove;
- wet/icy patches;
- tools/lanterns;
- glossy fabric/plastic
respond differently from snow/wood/canvas.

Avoid a single generic sky reflection over the entire scene.

### Practical local lighting
- furnace: warm local light + restrained emissive core;
- lanterns: low-range warm lights where composition needs them;
- environment: cool key/fill from winter sky;
- retain strong warm/cool separation without clipping furnace to saturated orange.

---

## 8. URP Decals — core AAA surface storytelling tool

Use Unity 6 URP Decal Renderer Feature / Decal Shader Graph rather than adding unique textures to every asset.

Required Havenline decal atlas categories:
- compacted/dirty snow;
- footprints and drag marks;
- soot around furnace/chimney;
- melt/wet halo around heat sources;
- frost/snow accumulation masks;
- canvas stains/repairs;
- wood scuffs/chop marks;
- metal rust/grime;
- pine needles/debris.

Use rendering layers so decals affect only intended receivers.

This is one of the highest visual-return techniques because it ties separately sourced assets into a shared physical world.

---

## 9. URP post-processing — included, restrained

Unity 6 URP already includes Bloom, Color Adjustments, Color Curves, Film Grain, Lift/Gamma/Gain, Shadows-Midtones-Highlights, Split Toning, Tonemapping, Vignette and White Balance.

Havenline baseline:
- ACES tonemapping audition for highlight rolloff and richer contrast;
- cool white balance for daylight, separately tuned night profile;
- subtle color adjustment/curves;
- low-cost bloom only around furnace/lantern highlights;
- very subtle film grain only if it survives phone-resolution evaluation;
- no heavy chromatic aberration or lens gimmicks.

Enable URP shader stripping for unused post-processing variants to avoid carrying effects Havenline does not use.

---

## 10. Ambient/contact depth

### URP SSAO Renderer Feature
Use low/medium-quality SSAO if profiling permits. Key targets:
- boots meeting snow;
- crates/logs contacting terrain;
- shelter ribs/flaps;
- furnace panels/pipes;
- tree roots and debris.

The goal is contact grounding, not dirty black halos.

### Decals + baked AO first
For Android baseline, prioritize baked AO and decals. SSAO is an enhancement tier, not a prerequisite for the scene to look finished.

---

## 11. Atmosphere tiers

### Baseline: URPFog — MIT
Source: https://github.com/meryuhi/URPFog
Current branch supports URP 17 and Unity 6000.x RenderGraph.
Use low-cost height/distance fog to add depth between camp and forest.

### Premium: CristianQiu URP Volumetric Light — MIT
Source: https://github.com/CristianQiu/Unity-URP-Volumetric-Light
Supports Unity 2022.3 through current Unity 6 releases, RenderGraph, orthographic projection and APV integration.
Use only after device profiling, around furnace/lantern/moon shafts. Not baseline.

---

## 12. Vegetation rendering tiers

### Source art
Use optimized LODs made from Poly Haven Pine/Fir/Saplings plus selected mobile tree donors.

### Shader layer
The Toby Foliage Engine / Light — free current Unity Asset Store package; use for mobile-oriented foliage/wind audition.

### Placement
Vegetation Spawner free.

### Premium Vulkan path
Unity 6 GPU Resident Drawer can use BatchRendererGroup/GPU instancing to lower draw-call/CPU cost, but it requires Forward+ and compute support and excludes OpenGL ES.
Decision: treat GPU Resident Drawer as a Vulkan/premium-device optimization path, not a universal Android baseline.

Baseline must remain performant with standard LODGroups, instancing-friendly materials, atlases and sensible tree counts.

---

## 13. LOD / texture policy for AAA-on-mobile

Source/master assets can be huge. Shipping assets cannot.

### Characters
- camera-close hero: 35K–80K tris can be acceptable only if actual device profiling supports it;
- normal isometric LOD1: target roughly 15K–35K;
- distant helper/enemy LOD2: lower aggressively;
- merge material slots where possible;
- keep face/skin resolution only where the camera can resolve it.

### Trees
- never ship 8M–17M scan masters;
- author LOD0/1/2 plus far billboard/impostor where required;
- reduce leaf-card overdraw;
- share atlases/materials by species.

### Props
- bake high-poly scan surface to low/mid poly;
- 2K hero, 1K common repeated props, lower for tiny items;
- use shared texture sets/atlases.

### Terrain
- tile PBR textures and decal variation instead of giant unique textures;
- compact snow path masks should carry story detail.

---

## 14. Recommended visual pipeline order

1. Replace all four procedural character visuals with free realistic survivor bodies/clothing.
2. Establish correct winter PBR material response and APV/reflection-probe lighting.
3. Replace flat terrain with layered scanned snow + real snow-bank geometry.
4. Replace white shell shelters with improved meshes + Quixel tarp materials + rope/seams/snow contact.
5. Replace furnace with Barrel Stove / realistic heater and real firewood/campfire detail.
6. Replace repeated toy pines with authored pine/fir/sapling/stump LOD family.
7. Add decals that physically integrate everything: tracks, soot, melt, dirt, pine debris, snow accumulation.
8. Add Animation Rigging contact correction to mocap.
9. Add baseline fog, restrained post and SSAO only after core assets already look good.
10. Render the exact Havenline shipping-camera proof set and visually reject anything still reading as prototype.

## Non-negotiable

No amount of post-processing can rescue primitive/prototype geometry. Effects are applied only after characters, shelters, furnace, terrain, trees and key props are replaced with credible assets.