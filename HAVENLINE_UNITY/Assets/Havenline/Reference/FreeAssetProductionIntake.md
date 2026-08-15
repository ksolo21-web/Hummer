# Havenline Free Asset Production Intake

Status: APPROVED DIRECTION — ASSET FILES REQUIRE LICENSE + PERFORMANCE INTAKE BEFORE PRODUCTION
Branch: havenline-unity-reference-rebuild

## Goal
Use high-quality free assets as production building blocks while preserving Havenline's approved stylized/animated identity and targeting 60 FPS as the production floor with 90/120 FPS modes on capable hardware.

## Art-direction rule
Third-party assets must NOT define the final visual language. Geometry, animation, vegetation, structures, props, textures, VFX, and source materials may be reused, but all production content must pass through a unified Havenline material/shader, lighting, palette, proportion, animation, LOD, collider, and performance pass before it can graduate into production scenes.

Do not allow Havenline to regress into a generic realistic-human survival look.

## Approved free-source candidates

### Environment / vegetation
- Saitama Studio — Stylized Trees Pack (Unity Asset Store)
- TriForge — Fantasy Worlds: Forest FREE (Unity Marketplace / Asset Store)
- NatureForge FREE — Stylized Meadow & Farm Kit (Fab)
- Holotna — Mountain: Stylized Fantasy Environment (Fab)
- Stylized Cozy Camp (Unity Asset Store)
- Low Poly Winter Log Cabin Pack (Unity Asset Store)
- Slavic Medieval Village Free (Unity Marketplace / Asset Store) — selective use only
- Free Vampiric PolyVania Town Pack (Unity Asset Store) — selective use only
- Quaternius Stylized Nature MegaKit — CC0 source geometry

### Characters / clothing / animation support
- Existing approved Havenline 2D character designs remain the visual source of truth for hero characters.
- Quaternius Modular Fantasy Outfits — CC0 secondary-character clothing/base pieces.
- Mixamo animations may be used as animation/retargeting sources when license requirements are satisfied; they do not replace Havenline's approved character designs.
- Generic free stylized player characters may be used only as rig/animation validation stand-ins unless separately approved.

### Materials / textures / lighting sources
- Poly Haven — CC0 source materials/HDRIs.
- ambientCG — CC0 source materials.
- Photoreal source maps must be stylized/regraded before production use.

### VFX / water / sky candidates
- Free Pack — Water Shader URP
- Stylized Water Effect Pack
- Stylized Skyboxes FREE
- Free stylized URP fire VFX candidates

### Rendering
- Evaluate Unity Toon Shader / UTS3 as a candidate for the shared Havenline stylized-material pipeline.
- A rendering package is not considered production-approved until it passes the existing visual bar and performance gates.

## Explicit exclusions / cautions
- Do not commit marketplace/Fab/Asset Store asset payloads until the license is captured and redistribution/source-control use is confirmed.
- Do not ship educational/demo content whose license is not commercial-game compatible.
- Do not blindly import entire packs into production scenes.
- Do not use third-party hero characters to replace approved Havenline character designs without direct visual approval.
- Do not mark an asset as accepted merely because Unity imports it or tests pass.

## Intake pipeline
Every third-party candidate must move through these gates:

1. LICENSE
   - Record source URL, publisher, license/EULA, version/date, and any attribution/redistribution requirements.
2. QUARANTINE
   - Import into an isolated intake area, never directly into production art folders.
3. TECHNICAL CLEANUP
   - Convert materials to the Havenline rendering path.
   - Normalize scale/pivots/naming.
   - Validate or create LODs.
   - Validate colliders.
   - Remove unnecessary scripts/shaders/demo scenes.
   - Cap texture sizes and compression by asset class.
4. STYLE CONVERSION
   - Apply the Havenline palette, shader response, outline/rim/highlight language, weathering, proportions, and lighting response.
5. PERFORMANCE
   - Test in an actual representative Havenline scene.
   - Measure CPU main-thread, render-thread, GPU frame time, batches, triangles/vertices, texture memory, shader variants, overdraw, particles, and animation cost.
6. VISUAL REVIEW
   - Direct frame review is mandatory.
   - Automated tests alone cannot approve visual quality.
7. PROMOTION
   - Only after all gates pass may the asset be copied/converted into production folders.

## Frame-rate targets
- 60 Hz target: 16.67 ms total frame budget — production floor.
- 90 Hz target: 11.11 ms total frame budget — supported quality/performance mode on capable devices.
- 120 Hz target: 8.33 ms total frame budget — high-refresh mode on capable devices.

Do not promise 120 FPS on every supported device. High-refresh modes must be selected using real device capability and thermal/performance headroom.

## Performance architecture direction
- Keep URP as the production renderer unless a later benchmark proves a superior path.
- Prefer GPU instancing / SRP Batcher compatible materials.
- Evaluate GPU Resident Drawer / Forward+ for compatible high-end configurations.
- Add explicit Application.targetFrameRate management for Android/high-refresh devices rather than relying only on vSync.
- Evaluate Unity Adaptive Performance for thermal/power-aware quality scaling.
- Build Havenline-specific quality tiers instead of relying on Unity's generic presets.
- Evaluate mip streaming, texture budgets, LOD distances, shadow distances/cascades, reflection usage, post-processing, particles, transparent overdraw, and dynamic resolution against the 60/90/120 frame budgets.

## Production recommendation
First environment test stack:
- Stylized Trees Pack
- Fantasy Worlds: Forest FREE
- NatureForge FREE
- Mountain: Stylized Fantasy Environment
- Stylized Cozy Camp
- Low Poly Winter Log Cabin Pack

Use Poly Haven / ambientCG only as source material where useful, then restyle through the Havenline material pipeline. Add stylized water, sky, fire, weather, and environmental VFX after the base scene holds the performance budget.

## Definition of done
A free asset is not "in Havenline" merely because it has been downloaded or imported. It is accepted only when:
- license provenance is recorded;
- it is visually converted to Havenline;
- it passes direct visual review;
- it passes representative-scene performance targets;
- it introduces no unacceptable package, shader, build, or maintenance dependency;
- and it is intentionally promoted to production.
