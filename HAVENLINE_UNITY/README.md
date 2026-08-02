# HAVENLINE — clean Unity production restart

This branch intentionally starts the production game over from a clean Unity project state.

The earlier generated Unity implementation is not carried into this branch. It is retained only in closed PR history for audit purposes.

## Engine and target

- Unity 6.3 LTS
- Universal Render Pipeline
- Android ARM64
- Landscape mobile presentation
- Galaxy Z Fold folded and unfolded layouts

## Non-negotiable rule

A source file or editor generator is not a completed game. HAVENLINE is only considered built when the Unity Editor has imported and compiled the project, the authored scene and prefabs exist as real Unity assets, Play Mode has exercised the core loop, and an Android APK plus Unity-rendered review captures have been produced and inspected.

See `Docs/PRODUCTION_REBUILD_CONTRACT.md` before implementing anything.