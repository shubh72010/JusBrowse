# JusBrowse Project State - Alpha 5.6
## Last Updated: 2026-03-05

## Completed Recently
- [x] **Deployment Bug Fix**: Restored JS injection on modern devices via `WebViewCompat.addDocumentStartScript` and fixed UA/Accept-Language request ordering.
- [x] **Phase 2: Secondary Vectors**: Implemented global spoofing for `hardwareConcurrency`, `deviceMemory`, `webdriver`, and `pdfViewerEnabled` across all engines.
- [x] **Phase 2.5: Extreme Entropy Reduction**: Forced `en-US,en;q=0.9` for HTTP `Accept-Language` and JS `navigator.language` by default.

## Current System Architecture
- **Injected Security Layer**: Uses both `addDocumentStartScript` (modern) and `evaluateJavascript` (fallback). Supports Default, Boring, and Persona modes.
- **Privacy Bus**: `FakeModeManager.kt` coordinates between personas. `normalizeLanguage` is `true` by default.
- **Network Stack**: `NetworkSurgeon.kt` handles header surgery and neutral responses for blocked trackers.

## Next Priorities
1. Phase 3: Network-Level Tracker Blocking (Trie-based domain/path matching).
2. Advanced ABP rule support for `ContentBlocker.kt`.
3. Automated regression testing for JS hook coverage.
