# Agent instructions

- Follow `docs/DESIGN.md`; `docs/FORMAT.md` is authoritative for persisted data.
- Use Kotlin, Jetpack Compose, and AndroidX Ink 1.1.0-alpha07. Do not introduce Flutter.
- Keep exactly one Compose `InProgressStrokes` at editor scope. `InProgressStrokesView` is an emergency escape hatch only if the Compose artifact disappears, not a second architecture.
- Do not add iText, Room as the notebook source of truth, EncryptedSharedPreferences, OEM pen SDKs, or unversioned experimental Ink features. The versioned custom pressure brush is allowed.
- Keep `:document` and `:ai-api` free of Android dependencies.
- Persist page coordinates in PDF points, origin at the displayed page top-left, y increasing downward.
- Every page mutation, including undo/redo, must use the same crash-safe page commit path.
- Keep architecture documentation current when module boundaries or persisted formats change.
- Prefer the smallest implementation that satisfies the locked v1 design; do not add silent fallback behavior.
