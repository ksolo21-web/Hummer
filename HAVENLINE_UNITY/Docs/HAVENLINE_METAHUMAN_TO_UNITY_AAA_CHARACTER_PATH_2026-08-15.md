# HAVENLINE — MetaHuman-to-Unity AAA Character Path

Date: 2026-08-15
Status: HIGH-PRIORITY CHARACTER PIPELINE CANDIDATE
Target: Unity 6000.3.18f1 / URP 17.3.0 / Android
Visual benchmark: approved Whiteout Survival gameplay segment.

## Why this route is now viable

Epic's current MetaHuman license page explicitly states that MetaHumans can be used with **any engine or creative software**. This is a material change from older MetaHuman-era restrictions and removes the old Unreal-only assumption.

Official license page:
https://www.metahuman.com/license

Current official summary observed 2026-08-15:
- MetaHuman is included under the standard Unreal Engine license.
- Epic states it is free to use for users/projects under the stated $1 million USD revenue threshold, with Unreal Engine standard terms applying above that threshold.
- MetaHumans can be used with any engine or creative software.

Licensing must be rechecked and archived at actual acquisition/export time. Havenline must not rely on obsolete pre-5.6 forum answers that said MetaHuman was Unreal-only.

---

## The quality opportunity

MetaHuman is a much stronger candidate for Havenline hero survivors than continuing to search only for random free FBX humans because it provides:
- photoreal head/skin/eye assets;
- mature rigging and deformation;
- scalable LOD definitions;
- card-based hair options suitable for lower platforms;
- built-in character creation and wardrobe workflow;
- DCC export paths for geometry/material/texture work outside Unreal.

Official MetaHuman documentation describes the system as scalable from cinematic to mobile quality.

Platform/LOD reference:
https://dev.epicgames.com/documentation/metahuman/platform-support-and-lod-specifications-for-metahumans

Epic's own Android guidance lists:
- best supported MetaHuman LOD: LOD 3;
- max texture size: 2048;
- hair: cards rather than strands.

Reference LOD 3 geometry from Epic's spec:
- head: about 2,500 vertices;
- body: about 1,507 vertices;
- hair cards: about 3,000 vertices;
- lower facial/physics complexity than cinematic LODs.

These numbers are Epic's internal MetaHuman guidance, not a guaranteed Unity import result. Havenline should use them as a mobile optimization benchmark while building a Unity-friendly Humanoid derivative.

---

## Export / conversion route

MetaHuman does not currently provide a one-click Unity runtime package. The viable route is DCC export and conversion.

Official export references:
- https://dev.epicgames.com/documentation/metahuman/metahuman-creator-export-tool-in-unreal-engine
- https://dev.epicgames.com/documentation/metahuman/metahuman-for-maya
- https://dev.epicgames.com/documentation/metahuman/saving-and-exporting-data

Recommended Havenline workflow:

1. Create four distinct survivors in MetaHuman Creator.
2. Use card-based hair from the start for the Android-target look.
3. Keep makeup/facial microdetail restrained because the isometric camera cannot resolve cinematic detail.
4. Export through MetaHuman's supported DCC pipeline.
5. Bring geometry/textures into Blender or Maya for a Unity-specific derivative.
6. Collapse/remove MetaHuman-only runtime systems that Unity does not need.
7. Retarget to a clean Unity Humanoid skeleton using AccuRIG/manual rig work as needed.
8. Retain high-quality skin/head normal/base-color data while simplifying materials.
9. Generate Havenline LOD0/LOD1/LOD2 meshes appropriate to actual screen size.
10. Use 2K maximum head/body textures for the Android build unless proof shows 1K is sufficient.
11. Rebuild hair as card-based Unity-compatible materials.
12. Fit winter clothing and backpacks to the final body/rig.
13. Use Mixamo/Rokoko base motion plus Unity Animation Rigging for interaction contact.
14. Validate every character in the actual Havenline shipping camera.

---

## MetaHuman optimized assembly as reference

Epic's optimized MetaHuman pipeline is also useful as a design reference even though Havenline ultimately needs a Unity-specific derivative.

Official reference:
https://dev.epicgames.com/documentation/metahuman/assembly

Epic reports the optimized assembly uses:
- compressed textures;
- less expensive optimized materials;
- more aggressive LODs;
- optimized animation/corrective settings;
- optimized hair settings;
- average character package sizes under ~100 MB rather than 1–2 GB cinematic packages.

Havenline should go further for Android by stripping Unreal-only assets, reducing material slots, using ASTC, and targeting the actual isometric camera.

---

## Free MetaHuman content already confirmed

### Epic Games — MetaHuman Techwear Outfit
Source:
https://www.fab.com/listings/9e04c752-1979-4723-b78f-6d24afc532bc
Observed: Free
Publisher: Epic Games
Format: MetaHuman parametric outfit
Use: technical wardrobe/LOD donor and potential base layer; visual design must be softened/de-techwear-ed for Havenline survival tone.

### Epic Games — MetaHuman Boots
Source:
https://www.fab.com/listings/c6596c37-e34f-46d4-a44e-fc360cc079c4
Observed: Free
Features: parametric, four body LODs, customizable color.
Use: footwear candidate/donor.

### Epic Games — MetaHuman Hightops
Source:
https://www.fab.com/listings/0bdf37d3-8ebf-4ec1-905d-65737b1c2809
Observed: Free
Use: secondary civilian footwear/NPC variation.

### Free parametric female clothing foundation
The Marilla Top:
https://www.fab.com/listings/dc6a2e50-8097-4ae0-b743-05bcd8559ad3
Observed: Free
Features: 4 LODs, optimized retopology, 4K virtual textures over 2 UDIMs, parametric feminine body support.
Use: topology/workflow donor rather than final winter outerwear.

The Clara Bodysuit:
https://www.fab.com/listings/8001aaeb-fb52-4cf0-bd3b-9f37100521f6
Observed: Free
Features: 4 LODs, optimized retopology, parametric feminine body support.
Use: underlayer/base clothing.

### Winter clothing strategy
A fully free parametric MetaHuman winter-parka listing was not conclusively confirmed in the current pass. Therefore:
- do not falsely label paid winter MetaHuman outfits as free;
- combine free MetaHuman bodies with the already-confirmed free generic puffer-jacket FBX assets in the Havenline asset manifest;
- fit/skin those jackets in Blender/AccuRIG/Unity;
- use free MetaHuman boots or the existing CC-BY game-ready winter boot donors;
- add free beanie/gloves/backpacks already catalogued.

This produces a custom Havenline winter survivor without paying for a MetaHuman winter outfit pack.

---

## Havenline survivor lineup proposal

Create four distinct silhouettes rather than four copies:

### Survivor A — primary male
- medium build
- quilted/puffer outer layer
- hiking/expedition backpack
- knit cap or short card hair
- rugged boots
- warm neutral/navy palette with restrained orange identification accent

### Survivor B — primary female
- distinct face/body silhouette
- long/oversized puffer derivative
- card-based tied/short hair or beanie
- gloves
- smaller backpack

### Survivor C — worker/helper
- bulkier insulated jacket
- work gloves
- tool loop / axe carrying points
- different headwear and backpack silhouette

### Survivor D — scout/rescue survivor
- lean silhouette
- lighter winter layer over base clothing
- compact pack
- high-contrast readable accent at shoulders/back so the isometric camera can identify the character quickly

No military uniforms as the default identity. Tactical donor assets can be de-militarized but Havenline should read as civilian survival, not a war game.

---

## Hair policy

Do not attempt cinematic strand/groom hair on Android.

Epic's mobile MetaHuman guidance uses hair cards at LOD 3. Havenline should follow the same principle:
- short/card hair or beanies for most survivors;
- minimize alpha overdraw;
- use one atlas/material per hair style where practical;
- test STP/TAA ghosting on thin cards;
- prefer silhouette stability over strand-level detail that cannot be resolved by the game camera.

---

## Face animation policy

The normal Havenline isometric camera does not need a full MetaHuman facial runtime.

Baseline:
- strip expensive facial systems from normal gameplay characters;
- keep only simple blink/idle expression or lightweight blendshapes if visually detectable;
- use full face rigs only for dedicated close-up/cinematic moments if the game later needs them.

The value of MetaHuman for Havenline is primarily **realistic human proportion, skin, head shape, clothing fit and deformation**, not hundreds of facial joints during normal gameplay.

---

## Material conversion to URP

MetaHuman materials are not copied verbatim into Unity.

Build custom Havenline URP materials:
- skin base color + normal + packed mask maps;
- simple subsurface-like approximation only if it survives mobile profiling;
- separate but low-count eye material;
- card hair shader;
- winter-clothing PBR materials with fabric micro-normal kept subtle at isometric scale;
- snow/frost accumulation through decals or shared shader masks rather than unique per-character materials.

Use Material Maker/Blender to repack/export textures cleanly.

---

## Acceptance gate

MetaHuman is not automatically approved because it is higher fidelity.

A survivor only replaces the current prototype if:
- it imports as a stable Unity Humanoid;
- animations retarget cleanly;
- no major jacket/limb clipping occurs during carry/gather/chop/rescue actions;
- card hair looks stable under STP/TAA and normal render scale;
- skin and clothing remain readable without becoming noisy at isometric scale;
- LOD transitions are not obvious;
- four characters remain distinguishable from the shipping camera;
- phone/tablet/fold/night proof frames look materially closer to the Whiteout Survival quality bar;
- device profiling meets the selected quality-tier budget.

## Bottom line

MetaHuman is now the highest-ceiling **free/standard-license character-generation route** found in the deep search, because current Epic licensing explicitly permits use with other engines. It should be tested side-by-side against the best confirmed free standalone FBX survivors. The winner is determined by actual Unity shipping-camera quality and Android performance, not marketplace reputation.