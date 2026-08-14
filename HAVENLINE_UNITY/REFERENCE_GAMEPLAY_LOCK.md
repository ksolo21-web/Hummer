# HAVENLINE Reference Gameplay Lock

This document locks HAVENLINE to the direct-control, proximity-driven gameplay language of the supplied playable reference, with the approved HAVENLINE crew, progression, world, and production improvements layered on top. The current runtime numbers are defined by `Assets/Havenline/Reference/HAVENLINE_REFERENCE_CONTRACT.json`; this document describes the player-facing rules and must not contradict that contract.

## Non-negotiable foundation

HAVENLINE is not an RTS, city-management screen, manual combat game, or button-heavy survival game.

The supplied example game is the gameplay, look, readability, animation-feel, graphics-quality, polish, camera-language, interaction-language, and moment-to-moment presentation target. HAVENLINE may add its own world, characters, systems, story, and progression, but it must not drift into a cheaper-looking or less readable interpretation of that target.

The player directly controls only the selected lead's movement. Almost every nearby action is proximity-driven and automatic. Character 1 and Character 2 are the only selectable leads. The unselected lead, Character 3, and Character 4 remain active as the three-person core companion crew; they do not replace the directly controlled lead.

The playable camera is a close three-quarter isometric view focused on a large, readable stylized survivor and the immediate outpost loop. The camera is not a distant base overview.

## Core interaction rule

When the player enters the valid range of an object or threat, the correct action begins automatically:

- Near a tree: automatically equip the axe and chop.
- Near a rock or salvage pile: automatically mine or dismantle.
- Near an animal or hostile enemy: automatically attack.
- Near a dropped resource: automatically collect it.
- Near storage, a furnace, construction point, or processing station: automatically deposit or process carried resources.
- Near a frozen survivor: automatically begin rescue/thaw interaction when its progression requirement is met.
- Near damaged defenses: automatically repair when carrying the required material.

There are no permanent chop, attack, gather, rescue, deposit, or repair buttons.

## Carrying rule — no gameplay cap

The selected lead can continue collecting resources without a gameplay carrying cap, matching the supplied example game.

- Gathering must never stop because an arbitrary inventory maximum was reached.
- `carryCapacity = 0` is the explicit runtime sentinel for unlimited carrying.
- The HUD shows the amount being carried, not `current / maximum`.
- Collected resources remain visibly represented on the character so the load reads immediately in motion.
- Rendering performance is allowed to use a compressed or pooled visual representation after the authored physical-stack budget is exceeded, but that visual optimization must never reduce, discard, or cap the underlying carried amount.
- The stack should look more substantial as the carried load grows rather than freezing visually at a small fixed count.
- Returning to a furnace, storage point, or construction site remains a player decision based on routing and progression—not because the game forces a full-inventory stop.

## Primary loop

1. Move out from the small safe area.
2. Enter a resource or threat zone.
3. Automatically gather or fight while the player controls positioning.
4. Show collected resources physically stacking on the survivor as the load grows.
5. Continue gathering as long as the player chooses; there is no arbitrary carry-cap interruption.
6. Return to the matching delivery or construction point.
7. Automatically unload the stack with visible transfer and satisfying feedback.
8. Spend or route delivered resources into an immediate visual upgrade.
9. Unlock more warmth, helpers, defenses, movement capability, efficiency, or a new nearby zone.
10. Repeat with a slightly larger or more valuable target.

The loop must remain understandable without reading instructions.

## Visual and polish target

The example game is the minimum bar for presentation quality. HAVENLINE must meet or exceed its readability and finish while keeping HAVENLINE's own art identity.

- Bright, premium, stylized 3D cartoon art.
- Chunky, appealing silhouettes and rounded forms.
- Clean high-quality materials with readable shapes instead of noisy realism.
- Cold blue snow and environment contrasted against strong warm orange furnace light.
- Large readable characters relative to the phone screen.
- Distinct faces, clothing, accessories, hair/fur, backpacks, tools, and silhouettes for all four core characters.
- Visible stacked logs, stone, salvage, fuel, and supplies that react to the carried load.
- Strong anticipation, impact, recoil, pickup, carry, drop-off, build, repair, rescue, attack, damage, and upgrade animation beats.
- Character feet must feel grounded; props must contact hands/backpacks correctly; carried items may not float or clip badly.
- Snow, ice, warmth, smoke, embers, particles, shadows, lighting, post-processing, and environmental motion must feel authored and cohesive rather than placeholder-like.
- Small dense play spaces connected into larger areas over time.
- Clear safe, danger, and resource zones without walls of UI.
- No generic primitive-looking final props, visibly repeated placeholder assets, broken texture seams, billboard-looking hero effects, or obvious AI reconstruction artifacts in accepted production content.
- Camera composition, UI scale, text contrast, motion, effects density, and visual hierarchy must remain readable on Galaxy Z Fold layouts as well as standard landscape Android screens.

## Camera and controls

- Close orthographic three-quarter isometric camera using the current runtime framing contract.
- The selected lead remains the visual focus.
- One transparent movement joystick is the main touch control; keyboard/controller equivalents may exist on supported platforms.
- Contextual progress rings, arrows, floating resource counts, and short labels appear only when useful.
- No large permanent task panel, furnace dashboard, weapon bar, attack button, hand button, or auto-mode button.
- The player should understand what is happening by watching the character and world.

## Automatic action behavior

Automatic actions must feel intelligent rather than passive:

- The survivor evaluates valid nearby targets with explicit action priority, proximity, and facing preference.
- A current target receives hysteresis so tiny distance changes do not cause flicker between targets.
- Deliberate player movement pauses an in-progress automatic action instead of destroying accumulated progress unless the target actually becomes invalid or leaves its hysteresis range.
- The survivor does not lock onto distant targets or remove movement control.
- Attack and gather timing is synchronized to animation impacts in the production Unity build.
- Damage, harvesting, pickup, depletion, carrying, and delivery occur on visible animation beats.
- The player can kite, reposition, choose targets by proximity, and leave danger by moving.

## Core crew rule

The active core crew size is four:

- Character 1 or Character 2: player-selected playable lead.
- The unselected playable lead: first core companion.
- Character 3: second core companion.
- Character 4: third core companion.

Characters 3 and 4 are not locked at the beginning. The three companions follow the selected lead using the runtime formation and may receive jobs as the game expands. The frozen survivor rescued during the opening loop is an additional helper NPC, not one of the four core characters.

No shipping scene may substitute one generic player model for this four-character roster. C1-C4 must come from the character production approval gate and remain fail-closed until their exact production assets and visual evidence are approved.

## Progression presentation

Progression must happen directly in the world:

- Furnace geometry, flame, smoke, light, and warmth radius visibly improve.
- Snow and ice recede as warmth expands.
- Frozen survivors thaw and become active helpers.
- Helpers visibly gather, carry, deposit, build, repair, guard, and heal.
- Structures appear through short visible construction stages.
- Barricades show damage and repair states.
- New paths, gates, tunnels, vehicles, and biome routes open physically.
- Character clothing, backpack, tools, weapons, efficiency, and visual load presentation can evolve, but default gathering remains uncapped.

Menus may support progression, but the primary reward is always visible in the play space.

## First frozen-outpost sequence

The opening playable sequence is intentionally compact and is locked to the current tested progression:

1. The selected lead begins at the frozen outpost with the other playable lead, Character 3, and Character 4 active as the three core companions.
2. The furnace begins at Level 1 with its smallest warmth radius.
3. Move to nearby trees and stone nodes; gathering begins automatically in range.
4. Continue collecting resources without a carrying cap. The visible load grows on the survivor while logical inventory remains unlimited.
5. Return to the furnace when the player chooses; furnace delivery happens automatically. Level 2 requires **18 wood and 6 stone** and must not upgrade early from wood alone.
6. At Level 2 the furnace visibly upgrades and warmth expands from 4.5 to 8.0 world units.
7. The frozen survivor becomes eligible for rescue; remaining in rescue range for the required 2.2 seconds activates the additional helper.
8. The rescued helper begins supporting gathering, delivery, repair, building, defense, and other assigned work while the four core characters remain active.
9. Gather and deliver **8 wood and 3 stone** to construct the north barricade through visible construction stages. The south barricade uses the same material requirement but is not the first-wave unlock gate.
10. The first wolf wave remains locked until all three conditions are true: furnace Level 2, survivor rescued, and north barricade completed.
11. Once unlocked, the first-wave pressure timer is 48 seconds. Wave 1 contains three wolves. The player, companions/helper systems, barricade, and furnace defense loop must remain readable and proximity-driven rather than turning into manual attack-button combat.
12. Clearing the pressure opens progression toward the connected area; later wave delays reduce by three seconds per completed wave but never below 24 seconds.

## Approved HAVENLINE improvements

These additions expand the reference formula without replacing it:

- A four-character core crew with C1/C2 lead selection and three persistent companions.
- Helpers gain jobs and scale into small teams.
- Defenses are built and repaired directly on the map.
- The world expands into connected frozen, forest, desert, underwater, sky, volcanic, swamp, ruined-city, underground, and alien biomes.
- Vehicles, tunnels, lifts, gates, airships, and submersibles connect regions.
- Hazards, resources, enemies, and automatic actions adapt to each biome.
- Persistent outposts and helper assignments continue across the connected world.
- Day/night, storms, temperature, and threats add pressure without turning play into menu management.
- Browser Three.js tools may accelerate visual QA and behavior prototyping, but Unity remains the authoritative production engine and Android release surface.

## Prohibited drift

Do not turn HAVENLINE into:

- a distant base-builder;
- an RTS overview;
- manual twin-stick or button-based combat;
- a realistic gritty shooter;
- a large compound filled with UI panels;
- a game where workers replace the directly controlled survivor;
- a menu-driven upgrade simulator;
- a static city where progress is represented only by numbers;
- a game that stops harvesting because a small arbitrary carry capacity was reached;
- a generic Whiteout Survival clone of the real 4X game;
- a browser-only project that bypasses the Unity/Android production truth gate;
- a shipping build that uses unapproved or generic placeholder characters in place of the four locked core identities;
- a visually unfinished build that passes mechanics tests while falling short of the supplied example game's graphics, readability, animation quality, or polish.

The target is the supplied example-game experience expanded into a complete, honest, premium HAVENLINE game, with HAVENLINE's own identity and every production claim backed by the actual Unity build and device evidence.
