# CURRENT_STATE.md

## Completed Tasks
- **FakeMode Crash Fix**: Replaced `currentPersona!!` force-unwrap with safe-call `?.let{}` in `SettingsScreen.kt` to prevent NPE during profile loading race.
- **AMOLED Theme Lock Fix**: Removed `surfaceVariant = Color.Black` from AMOLED override in `Theme.kt`, keeping UI elements (cards, radio buttons, previews) visible.
- **Duplicate ViewModel Fix**: Consolidated two `BrowserViewModel` instances in `MainActivity.kt` into a single Compose `viewModel()` with `LaunchedEffect` for intent handling.
- **Build Configuration Fix**: Enabled `buildConfig = true` in `app/build.gradle.kts` to allow source code access to generated `BuildConfig` (specifically `VERSION_NAME`).
- **Engine Toggle Exclusivity**: Removed dangling individual engine setters; all writes go through atomic `setActiveEngine()`.
- **Settings Migration**: Added `migrateIfNeeded()` for v0.0.4-5A upgrades.
- **Made by JusDots Card**: Premium footer card in Settings with version badge.

## System Architecture
- **Single ViewModel**: `MainActivity` creates one `BrowserViewModel` via Compose's `viewModel()`, stored in field for `onNewIntent`.
- **Engine State**: Atomic `setActiveEngine()` is the only write path.
- **Theme**: AMOLED override preserves `surfaceVariant` from theme preset for card/button visibility.

## Next Priorities
1. Full build verification
2. End-to-end test: FakeMode enable/disable cycle
3. Verify AMOLED + theme selection works correctly
