# HAVENLINE Character Lab

A browser-first QA and visual-review surface for Havenline character GLBs. It is intentionally separate from the production Unity runtime: Three.js is used for fast AI-assisted inspection and proof generation; Unity remains the authoritative game engine and Android build target.

## Why this exists

Character review was spending too much time on headless Unity/editor orchestration before basic visual questions were answered. This lab makes the review loop explicit and code-driven:

`Blender / GLB -> Character Lab QA -> approved four-view evidence -> Unity humanoid staging -> gameplay/deformation review -> Android build`

## Current capabilities

- Four persistent character slots (C1-C4) in one browser session.
- Local `.glb` / `.gltf` loading; files never need to be uploaded to a server.
- Approved-reference image loading per slot.
- Fixed Front / 3/4 / Side / Back orthographic review cameras.
- Reference-image overlay with adjustable opacity.
- Skeleton helper, wireframe, ground grid, exposure and framing controls.
- Automatic counts for vertices, triangles, meshes, skinned meshes, bones, materials, textures and embedded animation clips.
- Sampled skin-weight normalization check.
- Material audit that flags suspicious high-metalness character materials.
- Animation clip selection/playback when clips are embedded.
- Four-view PNG proof export.
- Machine-readable QA JSON export.
- Responsive layout that also works on phones/tablets for review.

## Run locally

The lab is static and uses a pinned Three.js CDN import. Do not open `index.html` directly with a `file://` URL; run any simple local HTTP server from this directory, for example:

```bash
python -m http.server 8080
```

Then open `http://localhost:8080`.

For automated/headless review, the lab also accepts `?slot=C1&model=<url>&reference=<url>` query parameters. This lets CI or a temporary local server preload one candidate without committing character binaries to the repository.

## Production rule

Passing this browser lab does **not** approve a character for the game. A character remains a review candidate until all of the following are true:

1. Its front, 3/4, side and back views are visually accepted against approved artwork.
2. Rig and skin-weight checks pass.
3. Unity imports it as the expected humanoid/avatar configuration.
4. Unity deformation/gameplay animation proof is accepted.
5. Android gameplay scale, lighting and performance are accepted.

## Version pin

The lab pins Three.js `0.185.0` (`r185`) so review behavior does not drift silently when Three.js publishes another release.
