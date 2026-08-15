# HAVENLINE — AAA Mobile Rendering Budget

Date: 2026-08-15
Verified project: Unity 6000.3.18f1 / URP 17.3.0
Primary visual benchmark: approved Whiteout Survival gameplay segment.

## Principle

Havenline should not target 120 FPS by destroying its art direction. The visual baseline is a polished frozen-survival scene at a stable 60 FPS. Higher refresh modes are quality/performance tiers that progressively reduce expensive effects and/or internal render resolution while preserving geometry, materials and composition.

Frame budgets:
- 60 FPS: 16.67 ms
- 90 FPS: 11.11 ms
- 120 FPS: 8.33 ms

Enable Android Optimized Frame Pacing so Unity distributes frames more evenly and avoids unnecessary presentation jitter.

Official references:
- Android frame pacing: https://developer.android.com/games/sdk/frame-pacing
- Unity Android Player setting: https://docs.unity3d.com/Manual/class-PlayerSettingsAndroid.html

---

## Rendering API tiers

### Tier A — Premium Android / Vulkan
Preferred for current high-end devices.

Enable/audition:
- Vulkan
- URP Forward+ only if the feature set and device profiling justify it
- SRP Batcher
- GPU Resident Drawer where compatible
- STP upscaling
- low-quality soft main-light shadows
- APV baked indirect lighting
- reflection probes
- selective SSAO
- restrained volumetric light only on devices that pass profiling

Why:
- Android documents Vulkan as the primary modern low-level graphics API and notes reduced driver overhead in draw-call-heavy scenes.
- Unity STP works on compute-shader-capable mobile and uses a mobile-tuned path.
- GPU Resident Drawer requires Forward+ and compute-capable platforms, so it belongs here rather than in the universal baseline.

### Tier B — Broad Android / OpenGL ES 3.x or Vulkan fallback
Baseline quality must remain visually finished without compute-only effects.

Use:
- standard URP Forward path unless profiling proves Forward+ better
- SRP Batcher
- LODGroups + shared materials + conventional instancing where appropriate
- baked/APV-style indirect lighting only where supported by the chosen graphics path/project configuration
- baked AO + decals
- standard shadow maps
- URPFog or very cheap distance fog
- no STP because Unity explicitly does not support STP on OpenGL ES
- no GPU Resident Drawer dependency

Do not allow the fallback tier to restore procedural/blocky art. It should reduce effects/resolution, not asset quality or silhouette quality.

---

## STP upscaling policy

Unity 6 URP Spatial Temporal Post-Processing (STP):
- is a software spatial/temporal upscaler;
- requires compute shaders / Shader Model 5.0 support;
- does not support OpenGL ES;
- automatically uses more performance-oriented filtering logic on mobile;
- operates with Render Scale rather than URP dynamic resolution;
- implicitly requires/enables a TAA preprocess.

Official references:
- https://docs.unity3d.com/Manual/urp/stp/stp-upscaler.html
- https://docs.unity3d.com/6000.0/Manual/urp/stp/stp-enable.html

Havenline audition matrix:
- 1.00 render scale: quality reference
- 0.90: first premium-performance test
- 0.85: likely practical candidate
- 0.80: aggressive test
- 0.75: only if image remains clean in foliage, thin ropes, hair and character silhouettes

Do not lock one scale without real Fold/Android proof frames and GPU timings. Thin foliage, hair, ropes, snow particles and motion need special scrutiny for temporal artifacts.

---

## Texture compression

Unity 6 Android default/recommended modern path is ASTC.

Official references:
- https://docs.unity3d.com/6000.0/Manual/android-requirements-and-compatibility.html
- https://docs.unity3d.com/6000.0/Manual/texture-choose-format-by-platform.html
- https://docs.unity3d.com/6000.0/Manual/texture-formats-reference.html

Havenline import targets:
- Hero skin/face, hero clothing normals, large shelter canvas: ASTC 4x4 only when visual proof shows the difference matters.
- Most environment PBR textures: ASTC 6x6.
- Distant foliage, masks, noncritical props: ASTC 8x8 when acceptable.
- Single-channel masks should avoid wasting four high-quality channels when a cheaper format/packing strategy is available.
- Pack metallic/roughness/AO/masks where shader workflow permits.

Important: if a device does not support the chosen compression format, Unity may decompress textures to uncompressed memory at runtime, increasing memory and potentially hurting rendering/loading. Maintain a compatible device policy rather than assuming every Android device handles every format.

---

## Mipmap streaming

Unity 6 can stream only the mip levels required by active cameras and obey a configured memory budget.

Official references:
- https://docs.unity3d.com/6000.0/Manual/TextureStreaming-use.html
- https://docs.unity3d.com/6000.0/ScriptReference/TextureImporter-streamingMipmaps.html
- https://docs.unity3d.com/6000.0/ScriptReference/QualitySettings-streamingMipmapsMemoryBudget.html

Havenline policy:
- Enable mipmap streaming for large character/environment PBR textures.
- Give hero character/hero furnace/close shelter textures higher streaming priority.
- Give distant trees, terrain breakup and background props lower priority.
- Keep tiny UI textures and small always-visible assets non-streaming when appropriate.
- Validate phone/tablet/fold camera changes for visible mip-popping.

The shipping camera is unusually helpful here because the isometric distance is bounded and predictable.

---

## Draw-call strategy

Unity 6 guidance prioritizes different batching paths depending on renderer configuration.

Official reference:
- https://docs.unity3d.com/Manual/optimizing-draw-calls-choose-method.html

Baseline rules:
- Keep SRP Batcher enabled.
- Share materials aggressively across repeated snow props, crates, logs and tree species.
- Use material variants rather than duplicating otherwise identical materials where possible.
- Atlas repeated camp props/foliage when it materially reduces state changes.
- Avoid MaterialPropertyBlock patterns that defeat the intended SRP batching path unless a measured case justifies them.
- Do not rely on dynamic batching; current Unity guidance no longer recommends it as the primary optimization strategy.

Premium Vulkan/Forward+ test:
- audition GPU Resident Drawer for static/repeated environment renderers;
- use Frame Debugger/Rendering Debugger to verify actual Hybrid Batch Groups and reduced SetPass/CPU cost;
- do not enable it merely because the checkbox exists.

---

## Shadow strategy

Havenline needs believable soft snow contact, but mobile shadow cost must stay controlled.

Official references:
- https://docs.unity3d.com/6000.0/Manual/shadows-optimization.html
- https://docs.unity3d.com/6000.0/Manual/urp/universalrp-asset.html
- https://docs.unity3d.com/6000.0/Manual/urp/shadow-resolution-urp.html
- https://docs.unity3d.com/6000.0/Manual/urp/renderer-feature-screen-space-shadows.html

Recommended baseline:
- one main directional light with realtime shadows;
- short main shadow distance centered on actual gameplay space;
- 1–2 cascades maximum unless proof shows a clear need for more;
- Low soft-shadow quality for the main light if device profiling passes;
- furnace/lantern practical lights generally do not cast realtime shadows in baseline mode;
- use baked AO, APV indirect light, decals and contact dirt to ground secondary objects.

Avoid by default:
- Screen Space Shadows on mobile. Unity notes they create a depth prepass and extra screen-space texture, which can hurt tile-based mobile platforms.
- multiple additional realtime shadow-casting lights.
- long shadow distances that waste resolution on empty perimeter snow.

A compact isometric play field is an advantage: reducing Max Distance increases useful shadow-map pixel density close to the camera.

---

## Lighting strategy

### Static world
- bake indirect lighting;
- use APV/local probe density around furnace, shelters and primary path network;
- use lower density in empty forest/perimeter zones;
- use baked reflection probes for metal, wet/ice and glass response.

### Moving survivors/wolves
Use probe/APV indirect response so dynamic characters receive believable cool environment light instead of reading as near-black silhouettes.

### Furnace
- visible emissive flame/core;
- one restrained warm practical light;
- soot/melt decals;
- baked or non-shadowed secondary warmth where possible.

Do not use a giant saturated orange emissive sphere as the primary lighting solution.

---

## URP post-processing budget

Baseline 60:
- color grading / white balance
- ACES tonemapping audition
- restrained bloom
- optional subtle vignette only if it improves composition

Premium only after profiling:
- SSAO
- subtle film grain
- premium fog/volumetric shafts

Avoid:
- motion blur for the core isometric gameplay view unless a targeted test proves value
- chromatic aberration as a default look
- expensive depth of field during gameplay

Unity explicitly notes post-processing can consume significant mobile frame time. Geometry/material/lighting quality comes first.

---

## Geometry budgets — audition targets, not hard pass/fail limits

### Hero survivor
Master source can be high resolution. Runtime targets:
- close-camera LOD0: approximately 35K–80K triangles if actual profiling permits
- normal gameplay LOD1: approximately 15K–35K
- distant/helper LOD2: lower aggressively
- cap material slots; merge tiny clothing materials where practical

### Wolves
- LOD0: roughly 15K–35K depending fur-card/normal-map design
- LOD1/2 aggressively lower

### Trees
Never ship raw multi-million-triangle scans.
- Hero near-camp tree LOD0: authored low/mid-poly derivative
- midground LOD1/2
- far billboard/impostor or very cheap mesh
- control alpha-card overdraw as carefully as triangle count

### Props
- firewood/crates/furnace/tent detail should be baked from scan/high-poly masters to sensible game meshes
- retain geometry only where silhouette or parallax matters at the actual camera distance

---

## Quality modes

### Quality — 60 FPS target
Preserve maximum visual character:
- best surviving character LODs
- STP if Vulkan and visually clean
- APV/probes
- Low soft main shadows
- selective SSAO
- full primary snow particles/fog budget
- highest accepted texture mips

### Performance — 90 FPS target
- lower render scale / STP candidate
- reduce SSAO or disable
- lower snow particle density
- lower fog cost
- earlier foliage LOD transitions
- maintain survivor geometry/material identity

### High Refresh — 120 FPS target
- treat as optional enhancement, not visual baseline
- aggressive render scale
- no volumetric effects
- SSAO off
- reduced particles
- earlier LODs
- shadows simplified
- preserve correct silhouettes/materials/animation; never swap back to blocky placeholder art

---

## Validation

For every quality tier capture:
- wide 1920x1080 equivalent
- close
- phone landscape
- tablet
- fold unfolded
- night

Measure:
- CPU frame time
- GPU frame time
- batches / SetPass calls
- triangles/vertices
- texture memory
- thermal behavior on device
- sustained frame pacing, not only average FPS

Then visually compare the actual images against the Whiteout Survival reference. A faster frame that looks like the rejected prototype is a failure.