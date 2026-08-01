# HAVENLINE Original-Reference Fidelity Standard

The Unity production rebuild must feel immediately recognizable as the same experience Kaleb originally supplied. It is not enough to make another frozen survival game with similar features.

## The required feel

- Close, polished three-quarter isometric presentation.
- The player remains large enough to read clothing, carrying, tool use, turning, and reactions.
- The playable frozen outpost feels compact, deliberately composed, and dense without becoming cluttered.
- The furnace is the visual and mechanical heart of the scene.
- The surrounding world reads as dangerous, cold, and expansive beyond the protected camp.
- Movement feels smooth and direct, with no loose camera, excessive acceleration, or tiny unreadable avatar.
- Nearby work happens automatically and visibly: approach, gather, carry, return, deposit, upgrade.
- Every action produces satisfying animation, motion, feedback, and state change.
- Survivors become useful workers rather than static collectibles.
- Construction and defense build outward from the furnace and visibly change the outpost.
- Enemy pressure is readable before impact and escalates with progression.
- The HUD is compact and subordinate to the game world.

## Camera and composition gate

The production camera must:

- use an orthographic or near-orthographic three-quarter isometric composition;
- preserve a readable player height on a phone screen;
- keep the active camp, furnace, nearby resources, and immediate threat lanes visible;
- avoid distant RTS framing, top-down map framing, dramatic orbiting, or cinematic camera drift;
- follow smoothly without making the world feel detached from the player;
- keep the furnace near the compositional center while allowing short excursions to nearby work areas.

## Character gate

Characters must:

- be smooth, stylized, production-quality 3D models rather than blocks, capsules, mannequins, or prototype people;
- have clear winter clothing silhouettes and visible carried supplies;
- use locomotion, gathering, carrying, depositing, working, reacting, rescuing, and combat/defense animation states;
- turn and transition naturally without sliding;
- show role differences for player, rescued survivor, helper, and enemy.

## World gate

The first frozen outpost must include:

- a visually distinctive upgradeable furnace;
- compact resource zones placed within readable travel distance;
- tents or shelter structures;
- build points for barricades and defense;
- snow, ice, rocks, trees, tracks, light, smoke, sparks, and warmth effects;
- a hard playable boundary disguised by environment design rather than an empty plane;
- threat approach lanes that can be understood at a glance.

## Core loop gate

The vertical slice is not accepted unless this entire loop is playable and visible:

1. Move with a mobile joystick or controller.
2. Approach a valid resource.
3. Automatically gather with animation and feedback.
4. Show the collected material on the character.
5. Return to the furnace.
6. Automatically deposit the material.
7. Upgrade the furnace.
8. Expand the warmth zone visibly.
9. Unlock or rescue a survivor.
10. Assign or observe the survivor performing a useful job.
11. Construct a barricade or defense.
12. Survive visible enemy pressure.
13. Continue expanding the outpost.

## Prohibited shortcuts

The production build must not be approved with:

- generated block art presented as final art;
- primitive characters or enemies;
- a large mostly empty snow plane;
- static resources with number-only gathering;
- invisible carrying or teleporting deposits;
- an oversized UI covering the play area;
- distant or unreadable camera framing;
- systems that exist only in code but are not visible in the actual scene;
- a compile-only APK presented as visual completion.

## Acceptance evidence

Before Android export is accepted, provide exact Unity-rendered evidence from the real scene showing:

- the close camera and player scale;
- the furnace-centered outpost;
- visible gathering, carrying, and depositing;
- the warmth-zone upgrade difference;
- a working survivor/helper;
- a barricade and approaching enemy;
- the compact phone HUD;
- measured performance on the target Android hardware.
