# Implementation Plan - Tactical Line Drawing, OpenRouter Free Models & Settings Audience/Persona Enhancements

This plan outlines the implementation of three major enhancements:
1. **Interactive Point-to-Point Line Drawing & Shape Measurement** on the Tactical Map with unit selection and Intel saving.
2. **OpenRouter Free Models** integration in the AI model selection catalog alongside Gemini free models.
3. **AI Audience Setting & Active Indicator Dots** (with "None" options) for both Personas and Audiences in Settings.

---

## User Review Required

> [!IMPORTANT]
> **Unit Conversion & Calculations**: Distances will be calculated in meters internally using standard geodesic formulas and converted on the fly to Feet, Millimeters, Meters, Kilometers, Miles, or Yards based on user selection.
>
> **OpenRouter Free Filtering**: The backend will query OpenRouter's `/api/v1/models` endpoint and filter for models with zero pricing (`prompt: 0`, `completion: 0` or `:free` suffix) to ensure they are truly free for free-tier users.

---

## Proposed Changes

### 1. Backend: OpenRouter Free Models (`aiService.ts`)
#### [MODIFY] [aiService.ts](file:///home/haykay34/AndroidStudioProjects/mistreal_mini/backend/src/services/aiService.ts)
- Update `getAvailableModels` to fetch models from OpenRouter API (`https://openrouter.ai/api/v1/models`).
- Filter OpenRouter models where pricing is free (`$0` or `:free`).
- Map them to the model response structure with `provider: 'openrouter'`, `price: 'Free'`, and `isProOnly: false`.

---

### 2. Data Persistence & Preferences (`SavedIntelEntity.kt`, `PreferenceManager.kt`)
#### [MODIFY] [SavedIntelEntity.kt](file:///home/haykay34/AndroidStudioProjects/mistreal_mini/app/src/main/java/com/example/mistreal_mini/data/local/entity/SavedIntelEntity.kt)
- Add support for type `"LINE"` or `"POLYGON"` with a JSON payload of vertices (`latitude`, `longitude` points) and selected measurement unit.

#### [MODIFY] [PreferenceManager.kt](file:///home/haykay34/AndroidStudioProjects/mistreal_mini/app/src/main/java/com/example/mistreal_mini/data/local/PreferenceManager.kt)
- Add DataStore keys and flows for `ai_audience` and supporting methods.

---

### 3. Tactical Map: Line Drawing & Measurement (`InteractiveMapView.kt`, `DashboardViewModel.kt`)
#### [MODIFY] [DashboardViewModel.kt](file:///home/haykay34/AndroidStudioProjects/mistreal_mini/app/src/main/java/com/example/mistreal_mini/ui/dashboard/DashboardViewModel.kt)
- Add state and methods for line/shape drawing mode, active measurement units (Meters, Feet, Miles, Kilometers, Yards, Millimeters), vertex lists, and saving shapes into Saved Intel.

#### [MODIFY] [InteractiveMapView.kt](file:///home/haykay34/AndroidStudioProjects/mistreal_mini/app/src/main/java/com/example/mistreal_mini/ui/dashboard/InteractiveMapView.kt)
- Add Leaflet handlers for point-to-point drawing, real-time segment distance calculation, floating measurement badges, and unit switching controls.

---

### 4. Settings: Audience & Persona Indicator Dots ("None" options)
#### [MODIFY] [SettingsViewModel.kt](file:///home/haykay34/AndroidStudioProjects/mistreal_mini/app/src/main/java/com/example/mistreal_mini/ui/settings/SettingsViewModel.kt)
- Expose `aiAudience` flow and save method. Support "None" options for both persona and audience.

#### [MODIFY] [SettingsScreen.kt](file:///home/haykay34/AndroidStudioProjects/mistreal_mini/app/src/main/java/com/example/mistreal_mini/ui/settings/SettingsScreen.kt)
- Add Audience selection section with "None" and preset options.
- Add active indicator dots (selection check/dot) for both Persona and Audience options.

---

## Verification Plan

### Automated Tests
- Run Gradle build (`./gradlew assembleDebug`) to verify zero compilation errors.

### Manual Verification
- Test drawing point-to-point lines on the tactical map, switching measurement units, and saving shapes.
- Verify OpenRouter free models appear in the AI chat model picker for free users.
- Verify Settings correctly displays audience options, active dots, and "None" states for personas and audiences.
