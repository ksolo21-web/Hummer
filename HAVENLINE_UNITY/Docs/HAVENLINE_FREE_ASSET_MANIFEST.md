# HAVENLINE — Free Production Asset Manifest

Status: ACTIVE ACQUISITION / AUDITION
Primary visual reference: the approved Whiteout Survival YouTube gameplay segment (furnace-centered frozen survival slice).

## Non-negotiable art rule

The current procedural/block-built characters and prototype-looking environment are placeholders only. Do not spend additional art effort polishing that shape language. New assets must materially improve silhouette, materials, animation, environmental density, and lighting while preserving close isometric gameplay readability.

The shipping target is a cohesive, premium frozen-survival presentation. High-resolution source assets are masters only: Android builds must use optimized textures, LODs, collisions, material counts, and draw-call budgets.

## License policy

- Prefer CC0/public-domain assets where possible.
- Fab assets must be acquired under the Fab Standard License at the time of download; retain acquisition evidence/version.
- Sketchfab CC-BY assets require attribution tracking in the project credits/license ledger.
- Unity Asset Store assets require the applicable Standard Unity Asset Store EULA.
- Reject personal-use-only, noncommercial, unclear-license, ripped-game, and redistribution-only sources.
- Never assume that a similarly named mirror has the same rights as the original listing.

## P0 — Character replacement

### APPROVED FOR AUDITION — Arberry / Survival Character FREE
Source: https://www.fab.com/listings/11d20d01-b764-4936-8163-cb20d05c369e
Price observed: Free
Format: FBX
Role: primary male survivor / hero base
Why: realistic PBR survivor, modular shoes/jacket/jeans/backpack/gloves, strong surface detail and readable silhouette.
Unity plan: import FBX as Humanoid; retarget Havenline locomotion/interactions; author cold-weather color/material variant; generate mobile LODs; reduce texture resolution in shipping build while retaining higher source masters.
Status: DOWNLOAD + UNITY CAMERA AUDITION REQUIRED

### APPROVED FOR AUDITION — Paul N / Free Nomad
Source: https://www.fab.com/listings/a0074d85-ecdc-4459-b8dc-d4cc8eae179f
Price observed: Free
Formats: Unreal Engine / Unity listing
Role: helper, scavenger, raider/NPC variation
Why: realistic scavenger with backpack and an existing Unity rig preview.
Status: DOWNLOAD + UNITY CAMERA AUDITION REQUIRED

### APPROVED FOR AUDITION — PKO Studio / Arctic Military Soldier
Source: https://www.fab.com/listings/2c938445-441c-457a-80ed-ea4a409dc965
Price observed: Free
Formats: FBX, OBJ
Role: cold-weather NPC base / clothing reference
Why: insulated winter gear, PBR textures, engine-compatible humanoid skeleton, Unity explicitly listed.
Constraint: de-militarize through materials/accessories so Havenline remains survival-first rather than military-first.
Status: DOWNLOAD + UNITY CAMERA AUDITION REQUIRED

### APPROVED FOR AUDITION — ChloeRobynSmith / Arctic Explorer Male Character
Source: https://sketchfab.com/3d-models/arctic-explorer-male-character-edea9d0701044ba3965dfb8cbdd20141
License: CC Attribution
Triangles observed: ~72.8k
Role: civilian winter survivor / expedition NPC reference
Why: strong arctic clothing silhouette and believable survival equipment without modern tactical styling.
Status: DOWNLOAD + ATTRIBUTION LEDGER + UNITY AUDITION REQUIRED

### FEMALE CHARACTER GAP
No candidate is promoted yet. Search continues for a genuinely high-quality, free, commercially usable civilian female winter survivor. Do not substitute a Roblox/voxel/obviously low-poly model or an unclear-license listing merely to fill the slot.

## P0 — Animation replacement

### APPROVED SOURCE — Adobe Mixamo
Source: https://www.mixamo.com/
License reference: Adobe Mixamo FAQ
Role: biped locomotion and survival interaction motion source
Target clips: idle variants, walk/run, turn, pick-up, carry, gather/chop, interact, injured, fall/death where suitable.
Constraint: retarget and blend inside Unity; do not ship stiff raw clips without transition/foot-contact cleanup.
Status: ACQUISITION AFTER CHARACTER SKELETON AUDITION

## P0 — Terrain / snow / forest materials

### APPROVED SOURCE — Poly Haven
Source: https://polyhaven.com/
License: CC0
Use: snow PBR, ice, rocks, logs/roots, trees/saplings, HDRIs.
Priority assets: Snow 01 / Snow 02; Pine/Fir tree and sapling sources; forest-floor breakup assets.
Constraint: source scans may be extremely dense. Build mobile LOD chains and use 1K/2K shipping textures where visual tests permit.
Status: DOWNLOAD MASTER SOURCES

### APPROVED SOURCE — ambientCG
Source: https://ambientcg.com/
License: CC0
Use: scratched/worn steel, corrugated metal, weathered wood, dirt, imperfections, fabric-adjacent surface breakup.
Priority: Metal 010; Wood 015; dirt/ground scans; scratches/imperfections.
Status: DOWNLOAD MASTER SOURCES

## P0 — Furnace / camp centerpiece

### APPROVED FOR AUDITION — GAMICO / Realistic Small Furnace
Source: https://sketchfab.com/3d-models/realistic-small-furnace-game-ready-hq-prop-2f5d5647fc3a413ea195934209bfa983
License: CC Attribution
Role: replace current cartoon/procedural furnace shell
Why: game-ready realistic furnace with separate glass/interior suitable for controlled emissive fire treatment.
Status: DOWNLOAD + ATTRIBUTION LEDGER + UNITY AUDITION REQUIRED

## P1 — Shelters / camp structures

### APPROVED FOR AUDITION — Nicholas-3D / Tents Model Free
Source: Sketchfab search/acquisition; verify listing license at download
Role: mobile-friendly fabric shelter base
Requirement: retain only after direct listing confirms CC-compatible commercial usage and textures survive close camera.
Status: VERIFY + AUDITION

### APPROVED FOR AUDITION — Sketchfab realistic/military tent candidates
Candidate examples: Military Tent / Game-ready Military Tent Unity / Realistic Tent
License target: CC Attribution only
Role: replace simple white wedge shelters
Required art pass: canvas color variation, snow accumulation, entrance depth, ropes/anchors, base integration; remove overt military markings.
Status: VERIFY INDIVIDUAL LISTING BEFORE DOWNLOAD

## P1 — Wolves / hostile wildlife

### APPROVED FOR AUDITION — Roo / Animated Wolf Scene
Source: https://sketchfab.com/3d-models/animated-wolf-scene-5d55506494e5460eaadf04370e07cd5c
License: CC Attribution
Triangles observed: ~11.8k
Animations observed: run, idle, crawl
Role: replace sliding/static prototype wolf presentation
Status: DOWNLOAD + ATTRIBUTION LEDGER + ANIMATION AUDITION REQUIRED

## P1 — Atmosphere / snow interaction / fire support

### APPROVED FOR TEST — AERO - Volumetric Fog and Mist
Source: Unity Asset Store
Price observed: Free
Pipeline: URP
Role: depth separation, low fog/mist, cold atmosphere
Constraint: profile on target Android hardware; use sparingly if raymarch cost is too high.
Status: ADD TO UNITY LIBRARY + PROFILE

### APPROVED FOR TEST — All Nature VFX
Source: Unity Asset Store
Price observed: Free
Pipeline support listed: Built-in / URP / HDRP
Use: snowstorm/falling snow/fog/flame particle building blocks.
Status: ADD TO UNITY LIBRARY + ART-DIRECTION PASS

### APPROVED FOR TEST — seasons/weather effects
Source: Unity Asset Store
Price observed: Free
Use: falling snow, snow, damp, fog and footprint references including wolf footprints.
Constraint: package is HDRP-authored; only particle portions are listed as URP-compatible, so test before integrating.
Status: ADD TO UNITY LIBRARY + COMPATIBILITY TEST

### APPROVED OPEN-SOURCE R&D — Sand Shader - Unity URP for Mobile
Source: https://github.com/TheodorKnab/Sand-Shader-Unity-URP-for-Mobile
License: MIT
Role: dynamic snow contact/deformation/track prototype using normal modification rather than geometry displacement.
Constraint: older Unity baseline; port selectively to current Havenline URP and profile.
Status: FORK/PORT AFTER LIVE UNITY CONNECTION

### APPROVED LIGHTWEIGHT FOG FALLBACK — URPFog
Source: https://github.com/meryuhi/URPFog
License: MIT
Role: depth/distance/height fog if volumetric fog is too expensive on Android.
Status: PROFILE AS FALLBACK

## P1 — Props / lived-in camp dressing

### APPROVED FOR AUDITION — Post-Apocalyptic Survival Props Pack (9 PBR Assets)
Source: https://www.fab.com/listings/4aea7fbc-0cfa-4e4e-9cc2-89cbfdc1e6ec
Price observed: Free
Formats: FBX
Useful meshes: wooden supply crate, jerry can, rusty barrel, tire stack; omit modern traffic props unless needed.
Status: DOWNLOAD + MATERIAL RECOLOR / SNOW PASS

### APPROVED FOR AUDITION — Army Green Supply Crate
Source: https://www.fab.com/listings/d7202d26-922b-414e-8903-44fb9b2a8038
Price observed: Free
Format: FBX
Role: storage/supply storytelling; recolor away from overt military language if needed.
Status: DOWNLOAD + UNITY AUDITION

### APPROVED FOR AUDITION — GAMICO / Realistic Oil Lamp
Source: https://sketchfab.com/3d-models/realistic-oil-lamp-game-ready-hq-survival-prop-73be3fbef7a7418b96b6583693d2f280
License: CC Attribution
Role: warm local accent, shelter/equipment dressing
Status: DOWNLOAD + ATTRIBUTION LEDGER

## Explicit rejects

- Voxel winter packs: wrong blocky visual language.
- Generic low-poly camping packs with flat shading: wrong visual language even when free.
- Personal-use-only wolf/model downloads: cannot ship commercially.
- Ripped assets from existing games: never use.
- Unclear-license assets: fail closed until rights are verified.
- High-poly scans imported directly into Android without LOD/texture optimization: fail performance gate.

## Unity audition order

1. Replace hero with Survival Character FREE and render the exact shipping camera.
2. Replace flat snow material + add authored snow breakup/tracks.
3. Replace furnace and shelters.
4. Replace repeated procedural pines with optimized real-source tree LODs.
5. Add helper/NPC and wolf replacements.
6. Add restrained snow/fog/fire atmosphere.
7. Add coherent supply clutter only after the major silhouettes/materials are fixed.
8. Render wide/close/phone/tablet/fold/night proof set and visually compare against the approved Whiteout reference before declaring the art pass complete.
