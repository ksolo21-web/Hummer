# HAVENLINE Unity Build State

- Engine: Unity 6.3 LTS (`6000.3.18f1`)
- Renderer: Universal Render Pipeline
- Target: Android ARM64 / IL2CPP
- Scene: compact furnace-centered frozen outpost
- Current gate: generate the exact Unity-rendered review frames and matching development APK
- Production release remains locked until visual and physical-device acceptance

This file marks the first exact CI attempt for the fresh Unity project. Build failures must be repaired from their concrete logs; the project must not fall back to Godot or loop through speculative rebuilds.
