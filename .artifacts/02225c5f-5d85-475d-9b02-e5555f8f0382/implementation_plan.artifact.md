# Startup Backend Wakeup & Supportive Truth-Teller Persona Implementation Plan

Implement a realistic startup warmup sequence with live dynamic counts (chats & intelligence feeds) from local cache/backend before the passcode screen, alongside a "Supportive Truth-Teller & Therapist" settings toggle featuring contextual historical gists every 4-6 turns.

## User Review Required

> [!IMPORTANT]
> - Startup flow will ping the backend warmup endpoint and query local Room database counts (`ChatDao` and `SavedIntelDao`) to show **real counts** (e.g., *"5 chats, 3 intelligence feeds ready"*), completely eliminating placeholders.
> - The new Settings toggle will enable the "Supportive Truth-Teller & Therapist" mode, modifying system prompts to combine honest professional feedback with empathetic guidance, plus a message counter triggering contextual gists and fun facts based on historical data.

## Open Questions

- None. The architecture and requirements are fully agreed upon.

## Proposed Changes

### Data & Preferences
#### [MODIFY] [PreferenceManager.kt](file:///home/haykay34/AndroidStudioProjects/mistreal_mini/app/src/main/java/com/example/mistreal_mini/data/local/PreferenceManager.kt)
- Add preferences for the "Supportive Truth-Teller / Therapist" persona toggle and message turn counter for contextual gists.

### Domain & Repository
#### [MODIFY] [AiRepository.kt](file:///home/haykay34/AndroidStudioProjects/mistreal_mini/app/src/main/java/com/example/mistreal_mini/data/repository/AiRepository.kt)
- Add warmup ping method and prompt modifier logic for the supportive truth-teller / therapist persona and historical gisting every 4-6 messages.

### UI & Startup Flow
#### [MODIFY] [SplashScreen.kt](file:///home/haykay34/AndroidStudioProjects/mistreal_mini/app/src/main/java/com/example/mistreal_mini/ui/splash/SplashScreen.kt)
- Implement realistic warmup sequence:
  1. Ping backend wakeup.
  2. Query actual chat count from `ChatDao` and intelligence count from `SavedIntelDao`.
  3. Display real-time status updates (e.g., *"Backend awake! Loaded 5 previous chats, 3 intelligence feeds..."*).
  4. Proceed to Passcode / Auth screen.

#### [MODIFY] [SettingsScreen.kt](file:///home/haykay34/AndroidStudioProjects/mistreal_mini/app/src/main/java/com/example/mistreal_mini/ui/settings/SettingsScreen.kt)
- Add the toggle for the "Supportive Truth-Teller & Therapist" persona mode.

## Verification Plan

### Automated Tests
- Build verification via `gradle_build` (`app:assembleDebug`).

### Manual Verification
- Launch app, verify live startup warmup messages show real counts from local DB / backend.
- Verify toggle in settings and test chat responses for truth-teller/therapist tone and periodic contextual gists.
