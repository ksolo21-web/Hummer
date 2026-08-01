# HAVENLINE — Unity 6 Production Rebuild

This is the new production foundation for HAVENLINE.

## Engine

- Unity 6.3 LTS (`6000.3.18f1`)
- Universal Render Pipeline (URP)
- Android-first, ARM64, IL2CPP
- 60 Hz minimum gameplay target with 120 Hz-capable presentation on supported devices

## Non-negotiable production direction

- This project starts clean from `main` and does not reuse the Godot runtime, generated scenes, patch scripts, or Godot project structure.
- The old Godot pull request is superseded and closed.
- The visual target is a close, polished three-quarter isometric frozen-survival game with readable stylized characters and a compact constructed outpost.
- Production scenes must use original HAVENLINE characters, world design, buildings, branding, UI, and approved production assets. The project intentionally fails validation when required production prefabs are absent; block primitives are not accepted as final art.
- Movement is screen-relative, smooth, and mobile-first. Gathering and delivery happen automatically near valid targets.
- Visible carried supplies, furnace upgrades, expanding warmth, survivor rescue/helper jobs, barricades, enemy pressure, and progression are first-class systems.
- The UI must support gameplay instead of covering the screen.
- Android export remains blocked until the exact Unity scene passes visual review and real-device acceptance.

## Project layout

- `Assets/Havenline/Runtime` — release runtime code
- `Assets/Havenline/Editor` — production validation and build gates
- `Assets/Havenline/Art` — original approved HAVENLINE production assets only
- `Assets/Havenline/Scenes` — authored production scenes
- `Assets/Havenline/Settings` — URP, input, quality, and production configuration

## First vertical slice

The first deliverable is one polished frozen outpost with:

1. close isometric camera and readable character scale;
2. joystick and controller movement;
3. automatic nearby gathering;
4. visible carried supplies and furnace delivery;
5. furnace upgrade and expanding warmth;
6. survivor rescue and helper automation;
7. barricade construction and wolf pressure;
8. compact mobile HUD;
9. measured Android performance and recovery from boundaries/falls.

No APK is considered approved merely because it compiles.