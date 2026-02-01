# Release Notes - JusBrowse Alpha 3.3 (v0.0.3-3)

We are excited to announce the release of JusBrowse Alpha 3.3! This update focuses heavily on security, privacy, and refining the user interface for a more premium experience.

## 🛡️ Security & Privacy
*   **Fake Mode (Digital Persona Spoofing):** Prevent fingerprinting by spoofing your User-Agent, screen resolution, hardware concurrency, language, and more.
*   **Fingerprinting Protection:** Built-in safeguards against common web tracking techniques.
*   **Flag Secure Toggle:** New setting to enable/disable `FLAG_SECURE`, allowing you to prevent screenshots and screen recordings of the app.
*   **WebView Hardening:** Enhanced origin-based permission controls and hardened network stack for safer browsing.
*   **Cookie Dialog Blocker:** Automatically blocks intrusive cookie consent banners.
*   **Enhanced Data Isolation:** Improved storage separation between standard and private tabs.

## 🎨 UI & UX Improvements
*   **Drag-to-Download:** A new, intuitive way to download files! Simply drag an image or link to the drop zone in the top-right corner.
*   **Theme Customization:** Added multiple predefined color schemes to personalize your browsing experience.
*   **Redesigned Tab Bar:** A more stable and visually polished bottom tab bar with improved navigation logic.
*   **Desktop Mode Enhancements:** Pinned tabs now appear as elegant icons, providing a true desktop-like feel.
*   **Visual Polishing:** Improved smooth transitions, refined address bar states, and updated iconography.

## ⚙️ Performance & Core Updates
*   **Session Persistence:** Robust backup and recovery for your tabs and session data using Android's backup system.
*   **Custom DNS Resolver:** Improved network privacy and reliability with an integrated DNS resolution layer.
*   **Database Stability:** Resolved Room database migration issues to ensure seamless updates.
*   **Bug Fixes:**
    *   Fixed a critical `NullPointerException` in the tab bar navigation.
    *   Optimized WebView memory usage.
    *   Resolved animations causing "white screen" flashes between navigation steps.

---
*Stay secure, stay private. Happy browsing!*
