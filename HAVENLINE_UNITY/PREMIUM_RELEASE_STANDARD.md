# HAVENLINE Premium Release Standard

HAVENLINE is a finished premium Android survival game project, not a prototype showcase. No APK may be described as a review candidate, release candidate, finished build, or premium build unless every gate below passes in the real Unity scene and on target Android hardware.

## Locked presentation

- Close three-quarter isometric presentation with a readable player, visible equipment, and smooth camera follow.
- Compact frozen outpost composed around the furnace as the visual and mechanical heart.
- Dense, authored environment design with terrain variation, snow accumulation, ice, rocks, trees, tracks, smoke, sparks, wind, fog, and warm/cold lighting contrast.
- World boundaries disguised by cliffs, snowbanks, forest density, wreckage, gates, and terrain composition. Empty planes and invisible-wall presentation are prohibited.
- Premium mobile HUD using a coherent HAVENLINE visual language. Flat debug boxes, legacy-font panels, emoji icons, and oversized prototype controls are prohibited.

## Production art gate

The shipping scene must use committed, licensed, production-approved assets under `Assets/Havenline/Art/Production`.

Required authored sets:

- Distinct player survivor with layered winter clothing, equipment attachments, readable face and silhouette, and optimized mobile materials.
- Distinct rescued survivor/helper with a different body, clothing, equipment, and role silhouette.
- Production wolf enemy with locomotion, attack, hit, stagger, death, and alert states.
- Upgradeable furnace with at least three visually distinct stages.
- Modular winter shelters, storage, barricades, gates, resource props, rocks, trees, snowbanks, ice, debris, and defense pieces.
- Final UI icon atlas, typography, controls, panels, objective treatment, status indicators, and transitions.

The following are prohibited from a premium build:

- `Superhero_*`, mannequin, capsule, cube, primitive, block-built, or placeholder characters.
- Randomly selected models based only on filenames.
- Build-time downloads used as unreviewed final art.
- Mixed asset packs with visibly conflicting scale, materials, proportions, or art styles.
- Unmodified free-pack presentation passed off as authored HAVENLINE art.
- Missing materials, default Unity materials, broken normals, floating props, or inconsistent texel density.

## Animation and interaction gate

The complete vertical-slice loop must be visible, animated, responsive, and readable:

1. Walk and sprint with natural acceleration, turning, stopping, foot placement, and snow feedback.
2. Approach resources and automatically gather with tool use, impact timing, particles, sound, hit reaction, depletion, and state change.
3. Show carried wood, stone, fuel, or salvage physically on the survivor.
4. Return and deposit with visible transfer, sound, particles, inventory change, and structure response.
5. Upgrade the furnace and visibly change geometry, lighting, fire, smoke, audio, and warmth-zone reach.
6. Rescue a survivor through a staged thaw/rescue sequence rather than an instant state switch.
7. Show the helper gathering, carrying, delivering, repairing, and defending with clear animation states.
8. Build and repair barricades with visible construction stages and damage states.
9. Telegraph wolf pressure before impact and provide readable attack, hit, damage, defeat, and defense feedback.
10. Continue expanding the outpost with visible world-state changes.

Sliding models, invisible gathering, teleporting deposits, number-only upgrades, and systems that exist only in code are release blockers.

## Lighting, rendering, and effects gate

- Unity 6 URP with linear color space, calibrated tone mapping, soft shadows, reflection support, light probes, and authored post-processing.
- Snow and ice materials must respond differently to light and cannot be represented by a single flat blue-white material.
- Furnace light must affect nearby characters, structures, snow, particles, and fog.
- Weather must include layered snowfall, wind response, drifting particles, localized ground effects, and visibility changes without obscuring play.
- Fire, smoke, sparks, footprints, gathering impacts, construction effects, enemy hits, warmth boundaries, and objective feedback must all be production quality.
- Effects must scale by device quality tier without changing gameplay readability.

## UI and user-experience gate

- Safe-area aware on phones, tablets, and foldables in both landscape orientations.
- Controls remain reachable and visually subordinate to the world.
- Objective text is concise and never blocks the player, furnace, resources, threats, or interaction feedback.
- UI uses final icons and typography; no emoji, legacy runtime font, debug labels, or temporary panels.
- Menus, pause, settings, onboarding, save/load feedback, accessibility options, and error handling must be complete.
- Touch, controller, and keyboard test input must not interfere with one another.

## Audio gate

- Original or properly licensed final music, ambience, weather, furnace, structure, resource, movement, UI, helper, and combat audio.
- Spatial audio must communicate nearby resources, furnace safety, helper activity, and approaching threats.
- No missing clips, placeholder beeps, synthetic debug tones, or repeated single-sample spam.
- Independent music, ambience, effects, voice, and haptics controls are required.

## Content and systems gate

The premium frozen-outpost release slice must include:

- Complete onboarding and first-session flow.
- Saving, loading, recovery from interruption, and safe migration of save data.
- Furnace progression, resources, helper automation, construction, repairs, defense, enemy waves, rewards, failure, recovery, and continued expansion.
- Tuned pacing rather than developer shortcuts or test-only state jumps.
- No dead buttons, unreachable objectives, soft locks, force closes, or systems exposed before they are functional.

## Android quality gate

- ARM64 production build, IL2CPP, no Development Build flag, no script debugging, and no debug overlays.
- Stable frame pacing with adaptive quality on supported Android phones, tablets, and foldables.
- Target 60 FPS on the primary device tier, with validated scalable tiers for weaker and high-refresh devices.
- Thermal, memory, battery, suspend/resume, rotation, fold/unfold, background/foreground, save integrity, and long-session tests completed.
- No missing shaders, pink materials, excessive overdraw, runaway particles, memory growth, or repeated garbage-collection stalls.

## Evidence required before release-candidate naming

The CI artifact must include Unity-rendered proof from the actual shipping scene showing:

- Close camera and readable final player.
- Finished furnace-centered outpost composition.
- Final terrain, structures, props, weather, lighting, and HUD.
- Gathering, carrying, depositing, and furnace upgrade states.
- Rescued helper working independently.
- Constructed and damaged defenses.
- Approaching and attacking enemies.
- Settings, pause, save state, and recovery flow.
- Device performance report with model, resolution, quality tier, average FPS, frame-time percentiles, peak memory, thermal state, and session length.

A compile-only APK, generated screenshot, static scene, prototype loop, or unverified build is not evidence of completion.
