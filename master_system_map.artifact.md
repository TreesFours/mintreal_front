# Master System Map: Mistreal Mini

This artifact is the single source of truth for the Mistreal Mini application. It tracks every UI element, functional logic, and historical evolution to ensure system integrity.

> [!IMPORTANT]
> **Safety Protocol**: Before modifying any existing feature, refer to this map. If you update a feature, add a new log entry to the **System Evolution Log** at the bottom. Never delete history.
> **INSTRUCTION:** Timestamps must be added ONLY to new entries added to the Evolution Log.

---

## **Phase 1: Entry, Splash & Secure Identity**
*Focus: Initial app launch and biometric/credential-based access.*

| Element | Action & Result | AI Status | User Verified | History & Notes | Code Ref |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Animated Logo** | `Psychology` icon scales (1.0 to 1.2) and fades in (0.0 to 1.0) over 2s. | `[completed]` | [ ] | Established branding entry point. | `SplashScreen.kt` |
| **System Motto** | Displays: *"If I'm powerful, I will make all what I want available to all like air."* | `[completed]` | [ ] | Defined application philosophy. | `SplashScreen.kt` |
| **Auth Toggle** | Swaps between "Sign In" and "Create Account" modes. | `[completed]` | [ ] | Dynamic UI for agent onboarding. | `AuthScreen.kt` |
| **Email/Pass Input** | Secure entry fields with focus management. | `[completed]` | [ ] | Added `pointerInput` to dismiss keyboard. | `AuthScreen.kt` |
| **Login Trigger** | Submits credentials via Hilt `AuthViewModel`. | `[completed]` | [ ] | Trigger login on `ImeAction.Done`. | `AuthScreen.kt` |

---

## **Phase 2: Intelligence Dashboard (Command Center)**
*Focus: Real-time data streams, global intelligence, and multi-platform dispatch.*

| Element | Action & Result | AI Status | User Verified | History & Notes | Code Ref |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **NASA APOD BG** | Fades in [Astro] news background image. | `[completed]` | [ ] | Dynamic global background sync. | `DashboardScreen.kt` |
| **Weather Card** | Displays location, condition, and rain alerts. | `[completed]` | [ ] | Real-time environmental sync. | `DashboardScreen.kt` |
| **Compass HUD** | Tactical bearing tied to Rotation/Mag sensors. | `[completed]` | [ ] | **[UPGRADE]:** Smoothed jitter; Upright mode. | `DashboardScreen.kt` |
| **City Search Bar** | Performs geocoding and triggers Map. | `[completed]` | [ ] | **[UPGRADE]:** Region bias for local results. | `DashboardScreen.kt` |
| **Dispatch Center** | Multi-post functionality to connected accounts. | `[completed]` | [ ] | Target platform selection (X, WA, etc.). | `DashboardScreen.kt` |

---

## **Phase 3: Tactical Map (Strategic Overlay)**
*Focus: Interactive mapping, location history, and navigation intelligence.*

| Element | Action & Result | AI Status | User Verified | History & Notes | Code Ref |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Leaflet WebView** | Full-screen interactive hybrid map. | `[completed]` | [ ] | **[UPGRADE]:** Satellite + Terrain layers. | `InteractiveMapView.kt` |
| **Tactical Pins** | Drop pins (long-press) with precise labels. | `[completed]` | [ ] | Multi-pin support with street-level names. | `InteractiveMapView.kt` |
| **Discovery Logic** | Aggregated search around all active pins. | `[completed]` | [ ] | Distance-aware categorized intelligence. | `InteractiveMapView.kt` |
| **Circle Draw Tool** | Tactical circle for localized satellite focus. | `[completed]` | [ ] | Satellite focus pop-up for specific areas. | `InteractiveMapView.kt` |
| **"You" History** | Logs physical movement history (GPS). | `[completed]` | [ ] | Dedicated Room-backed GPS tracking. | `InteractiveMapView.kt` |
| **Intel Log** | Logs strategic searches and tactical pins. | `[completed]` | [ ] | Separated from physical history log. | `InteractiveMapView.kt` |

---

## **Phase 4: Tactical AI Chat (Intelligence Hub)**
*Focus: AI interaction, voice protocols, and record keeping.*

| Element | Action & Result | AI Status | User Verified | History & Notes | Code Ref |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Platform Sidebar** | Drawer for switching communication channels. | `[completed]` | [ ] | Multi-channel communication hub. | `ChatScreen.kt` |
| **Voice Bar** | Professional tactical audio handling. | `[completed]` | [ ] | STT/TTS integration with Room persistence. | `ChatScreen.kt` |
| **Attachment Bar** | Visual Sync, Optic Intel, Data Pkg. | `[completed]` | [ ] | Multi-modal data ingestion. | `ChatScreen.kt` |
| **AI Map Context** | AI analysis using pin/discovery data. | `[completed]` | [ ] | Real-time map state fed to AI prompts. | `ChatScreen.kt` |

---

## **System Evolution Log (Chronological)**

| Date/Timestamp | Feature/Component | Change Description | Status |
| :--- | :--- | :--- | :--- |
| 2026-08-17 17:00 | Map - Search Logic | Increased Geocoder results to 10; Added location disambiguation for multiple results. | Completed |
| 2026-08-17 17:00 | Map - UI | Added top-level Search Bar to Interactive Map View. | Completed |
| 2026-08-17 17:00 | Compass | Enhanced logging for azimuth/orientation debugging. | Completed |
| 2026-08-17 17:30 | Map - Regressions | Fixed search city navigation; Restored immediate fly-to logic for single results. | Completed |
| 2026-08-17 17:30 | Map - Multi-Pin | Implementing multi-pin selection (long press) and dynamic discovery around pins. | Completed |
| 2026-08-17 17:30 | Map - UI Persistence | Ensuring Intel, Explore, You, and Search buttons are never blocked or hidden by map. | Completed |
| 2026-08-17 17:30 | Compass - Stability | Implementing low-pass filter and coordinate remapping for upright device orientation. | Completed |
| 2026-08-17 17:30 | Compass - Calibration | Adding user notification/instruction for compass calibration when opening map. | Completed |
| 2026-08-17 18:20 | Map - Intel Log | Separated Physical History (YOU) from Strategic Searches/Pins (INTEL LOG). | Completed |
| 2026-08-17 18:20 | Map - Aggregated Intel | Multi-pin discovery results now grouped by category with "near pin X" labels. | Completed |
| 2026-08-17 18:20 | Map - Precision Labels | Pins now automatically display precise labels (Street/Neighborhood) via reverse geocoding. | Completed |
| 2026-08-17 18:20 | Map - UI Overhaul | Restored Location Button (target icon); Fixed Z-layering for persistent, non-blocking buttons. | Completed |
| 2026-08-17 19:10 | Map - Satellite Intel | **ADJUSTMENT:** Implementing high-detail hybrid map with grass/hills and terrain visualization. | Completed |
| 2026-08-17 19:10 | Map - Circle Tool | **NEW FEATURE:** Tactical Draw tool to create resizable focus circles for area-specific intelligence. | Completed |
| 2026-08-17 19:10 | Map - Discovery UI | **ADJUSTMENT:** Discovery markers now show place names and exact distance from focus point. | Completed |
| 2026-08-17 19:10 | Map - Search Precision | **ADJUSTMENT:** Search bias added to prioritize Nigeria over Japan; Distinct Red Marker for search results. | Completed |
| 2026-08-17 19:10 | Compass - Wizard | **NEW FEATURE:** Clickable HUD to open professional Calibration Wizard with figure-8 instructions. | Completed |
