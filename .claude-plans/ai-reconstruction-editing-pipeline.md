# AI Reconstruction & Editing Pipeline — Implementation Plan

*Plan date: 2026-10-06. Written against the codebase at `/home/haykay34/AndroidStudioProjects/mistreal_mini` as of commit `a458fc3` on the current branch. This is a self-contained planning document for a feature that has not yet been started; it does not assume any state beyond what's described here.*

## 1. Feasibility Check (read first — this reshapes the rest of the plan)

I traced the actual request shapes sent today, not just the public capabilities of Imagen/Veo in the abstract.

**Imagen (`backend/src/services/aiService.ts`, `generateImage`)** — sends:
```ts
axios.post(`.../models/${IMAGE_GEN_MODEL_ID}:predict?key=${apiKey}`, { instances: [{ prompt }], parameters: { sampleCount: 1 } })
```
Text-only. No image field exists in this call at all. Imagen-3 via this REST surface has no documented reference-image input in this integration's current form — adding one would mean widening this call's `instances[0]` payload (Imagen does support image-conditioned variants in some surfaces, but this code doesn't use them and that would need separate verification).

**Veo (`generateVideo`)** — sends:
```ts
axios.post(`.../models/${VIDEO_GEN_MODEL_ID}:predictLongRunning?key=${apiKey}`, { instances: [{ prompt }], parameters: { sampleCount: 1 } })
```
Also text-only, and the function's own comment block already flags this whole call as **"UNVERIFIED... have NOT been exercised against a real API key yet."** So even the baseline Veo integration isn't confirmed working, let alone an image-conditioned variant.

**Gemini image edit (`editImage`, `IMAGE_EDIT_MODEL_ID = 'gemini-2.5-flash-image'`)** — sends:
```ts
contents: [{ parts: [{ text: prompt }, { inline_data: { mime_type, data: imageBase64 } }] }], generationConfig: { responseModalities: ['IMAGE'] }
```
This is the most promising path: Gemini's multimodal `generateContent` architecturally accepts multiple `inline_data` image parts in one request (this is how "nano banana"-style models do image composition/editing across several inputs). **But today the code only ever sends one image** — `imageDatas[0]` is hardcoded in `aiService.ts`, and `ChatViewModel.editImageWithAi()` only ever passes `imageUris = listOf(imageUri)`.

**A critical existing-but-broken discovery — "Scene Mode":** `ChatInputBar.kt` already has a toggle (Movie icon) that labels attached images `START`/`END`/`EXTRA`, and `SendMessageUseCase.kt` already names the resulting multipart parts `"start_frame"` / `"end_frame"` for indices 0/1. **This looks exactly like a half-built version of the keyframe feature being requested.** However:
- `backend/src/index.ts`'s multer config is `upload.fields([{ name: 'images', maxCount: 5 }, { name: 'audio', maxCount: 1 }, { name: 'video', maxCount: 1 }])` — it only declares `images`, `audio`, `video`. Parts named `start_frame`/`end_frame` are **not in that allowlist**, so multer will reject them (`LIMIT_UNEXPECTED_FILE`) whenever Scene Mode is on and images are attached.
- Even if that were fixed, `generateVideo()`/`VideoEditProvider.edit()` never read any image field — nothing downstream does anything with a start/end frame even if it arrived safely.

So Scene Mode today is UI scaffolding that doesn't functionally deliver keyframe conditioning, and most likely actively breaks the chat request when toggled on with images attached. This is highly relevant because **Phase A below is substantially "finish/repair existing half-built infrastructure," not "build from zero."**

**BYOK generic adapters (`imageGenProvider.ts`, `videoEditProvider.ts`)** send only `{ prompt, model[, video, mimeType] }` — no array of reference images. Real third-party providers that *do* natively support start/end-frame video conditioning exist (Runway Gen-3/Aleph, Luma Dream Machine, Kling) and are reachable only via this BYOK path (`baseUrl`/`modelName`/custom proxy) — this is the most realistic near-term route to genuine start/end-frame **video** generation, since it doesn't depend on verifying Veo's exact request shape.

**Transport layer is already multi-image-capable end to end:** `AiApiService.kt`'s Retrofit interface declares `images: List<MultipartBody.Part>?`, and multer's `images` field allows `maxCount: 5`. This scaffolding already supports several images per request — it's just not used beyond 1 today for editing. This materially lowers the cost of Phase A/B for images.

**Bottom line:**
| Capability | Buildable now? | Path |
|---|---|---|
| Multi-image input (start/end/character ref) for **image** generation/editing | Yes | Extend Gemini `editImage()` to accept N `inline_data` parts (already-working pattern, just widen it) + fix the `images` multipart field usage (stop using fake `start_frame`/`end_frame` field names) |
| Multi-image keyframe input for **video** generation via Veo (app's own key) | Unverified / likely no | Current call is text-only and itself unverified; needs live API verification before relying on it |
| Multi-image keyframe input for **video** via BYOK | Yes, with work | Extend `VideoEditProvider`'s generic adapter to forward `startImage`/`endImage`/`referenceImage` fields; requires the user's own provider (Runway/Luma/Kling-style) to honor that shape |
| Freehand pencil+text region annotation fused into one request | Yes | Natural extension of `AttachmentEditorDialog.kt`'s existing segment-notes fusion pattern |
| Per-scene video annotation/tracking + final multi-scene assembly | Partially | Per-scene drafting is buildable via existing trim (`MediaEditorUtil.trimVideo`) + per-clip AI edit; actual video *stitching* of multiple AI-generated clips back together is genuinely new work — Media3 Transformer is currently only used for trim, not concatenation |

## 2. UI/Interaction Design — extend `AttachmentEditorDialog.kt`, don't parallel it

Recommendation: extend, not replace. The existing foundation is substantial and exactly shaped for this:

- `EditorMode` enum (`VIEW, CROP, TRIM, SEGMENT`) — add a new mode, e.g. `ANNOTATE`, reached either as its own button or as an in-place tool inside `SegmentEditor` when a segment/region is tapped (currently tapping a thumbnail opens a text-only `AlertDialog` with `OutlinedTextField`).
- `SegmentEditor` already renders a `LazyRow` of 6 region/segment thumbnails (`extractImageRegionThumbnail` for images, `extractSegmentThumbnail` for video) with a green pencil icon badge when a note exists (`segmentNotes[index]`). This is the natural anchor point for "this region has a markup."
- `CropEditor`'s drag-gesture math (`detectDragGestures`, fractional `0f..1f` coordinates relative to `boxSize`) is a directly reusable pattern for freehand pencil strokes — a `Canvas` + `pointerInput { detectDragGestures { ... } }` capturing a list of `Offset` points in the same fractional coordinate space, with a color picker row (reuse Compose `Color` swatches) and a stroke-width slider.
- When the user taps a region/segment: open an annotate surface showing that region/frame at larger scale, let them draw (pencil + color), and keep the existing text field below it for the paired description ("this hand has 5 fingers, fix here"). Save both together, not as two disconnected pieces of state.
- For **rendering the markup to the AI**: rasterize the strokes onto a copy of the relevant image/frame (full image, not just the cropped region, so spatial context is preserved) and send it as an **additional `images` multipart part** alongside the original — reusing the already-working multi-image transport rather than inventing a new channel. The paired text ("Region 3 (marked in red): ...") stays in the fused instruction text block exactly as today's `segmentFusionBlock` pattern already does for text-only notes. This mirrors how real inpainting-mask-plus-instruction workflows operate and gives the model the two things it actually needs: *where* (visual pointer) and *what* (text).
- **Iterative back-and-forth**: today, `editImageWithAi`/`editVideoWithAi` deliberately bypass chat history (`history = emptyList()` — "isolated media operation, not a normal chat turn," per the existing code comment). Genuine "back and forth, not one-shot" editing requires a small **per-attachment edit history** (not the global chat log) threaded into each follow-up call, so a second round of markup is interpreted as a refinement of the AI's prior attempt rather than a fresh edit. This is a real, scoped architecture change, not just a UI addition.

## 3. Data Model

Extend the existing `Map<Int, String>` (`ChatViewModel._attachmentSegmentNotes`, `segmentNotes` parameter in `AttachmentEditorDialog`) rather than replacing it outright — minimizes churn in the one place that already threads this through `sendMessage()`'s fusion logic.

```
AnnotationStroke(points: List<Offset /* fractional 0..1 */>, color: Color, widthFraction: Float)

SegmentAnnotation(
    segmentIndex: Int,
    textNote: String,
    strokes: List<AnnotationStroke>,
    rasterizedOverlayUri: Uri?        // produced lazily when the request is actually sent
)

// Replaces today's Map<Int, String> for segmentNotes once pencil support lands:
typealias SegmentAnnotations = Map<Int, SegmentAnnotation>

KeyframeReferenceSet(                 // whole-media-level, not per-segment — new, separate structure
    startImageUri: Uri?,
    endImageUri: Uri?,
    characterReferenceImageUri: Uri?,
    characterDescription: String
)

// Video-only, Phase C:
SceneDescriptor(
    sceneIndex: Int,
    startMs: Long, endMs: Long,
    thumbnail: Bitmap?,
    status: DRAFT | REVISED | APPROVED,
    annotations: SegmentAnnotations,   // reuses the same per-region structure, scoped to this scene's representative frame
    draftResultUri: Uri?,              // the AI's first-pass reconstruction for this scene
    revisionHistory: List<String>      // each round's fused instruction text, for traceability across the "back and forth"
)
```

**Fusion into the existing "one consolidated instruction" architecture:** per the commit reasoning already in the codebase ("Auto-route generation/edit requests + extend 6-way split to images" — independently-edited segments would have visible seams without real video-synthesis capability), annotations must stay fused into a single request, never stitched after independent edits. Concretely: build the fused text block exactly as `segmentFusionBlock` does today, but reference each annotated region/scene's rasterized overlay image by position ("see attached marked-up image for Region 3") and attach all overlay images as additional `images` parts in the same request.

**Multi-scene assembly (Phase C):** because no provider used here does automatic cross-scene consistency, "nothing gets lost across scenes" has to be achieved by carrying forward the *previous* approved scene's result as a continuity reference image into the *next* scene's generation request — not by any native multi-scene API feature. True video concatenation of the resulting per-scene clips is separate, new work (see §4 Phase C).

## 4. Phasing

**Phase A — DONE (2026-10-06)**
- Fixed Scene Mode's broken transport: images now always ride under the `images` multipart field (the only one the backend's upload config allows) with a parallel `imageRoles` JSON array (`start`/`end`/`character`/`extra`) saying which is which, instead of the old `start_frame`/`end_frame` named fields multer was silently rejecting.
- Added a 3rd "Character" reference slot to Scene Mode's attachment UI.
- Bumped `VIDEO_GEN_MODEL_ID` from `veo-2.0-generate-001` to `veo-3.1-generate-preview` and wired `instances[0].image`/`lastFrame` (`inlineData.{mimeType,data}`) into `generateVideo()` — real start/end-frame conditioning on the app's own key, same endpoint pattern as before.
- `editImage()` now sends every attached image (not just `imageDatas[0]`) as separate `inline_data` parts, so a character reference actually reaches the model during an edit.
- `VideoEditProvider` (the BYOK adapter) forwards `startImage`/`endImage`/`referenceImage` best-effort to whatever custom provider the user has configured.
- Files touched: `SendMessageUseCase.kt`, `ChatInputBar.kt`, `AiApiService.kt`, `AiRepository.kt`, `index.ts`, `aiService.ts`, `videoEditProvider.ts`.

**Phase A (original scope) — Keyframe reference inputs for single-shot generation/reconstruction**
- A1 (repair + complete): Stop using the broken `start_frame`/`end_frame` multipart field names. Either add them to multer's `fields()` allowlist in `backend/src/index.ts`, or (recommended, more general) keep everything under the existing `images` field and pass a parallel `imageRoles` JSON part (array of `"start"|"end"|"character"|"region_N"` aligned by upload order) so future roles don't require widening the multer allowlist each time.
- Extend `editImage()` (or a new `reconstructImage()`) in `aiService.ts` to build `contents.parts` from N images + roles instead of hardcoding `imageDatas[0]`.
- For video: default to BYOK (extend `VideoEditProvider`'s adapter to forward `startImage`/`endImage`/`referenceImage` fields) since Veo's own image-conditioning support on this API surface is unverified. Gate true Veo-native keyframe video behind verification (see Open Questions).
- UI: repair/extend the existing Scene Mode attachment flow in `ChatInputBar.kt` (already has START/END/EXTRA labeling) to add a "Character" slot + character-description text field, wired to the fixed transport.
- **Achievable now** for the image leg; **BYOK-dependent** for the video leg.

**Phase B — DONE (2026-10-06)**
- `AttachmentEditorDialog.kt`'s region-annotate dialog now has a freehand drawing canvas (5-color picker, drag-to-draw, clear) layered over the region thumbnail, alongside the existing text note field — image attachments only (video segment annotation is Phase C, see below for why it doesn't fit cleanly yet).
- Strokes are stored region-local (0f..1f of that region's crop) in `ChatViewModel._attachmentSegmentDrawings`, keyed by attachment Uri, mirroring `_attachmentSegmentNotes`'s existing lifecycle (removed/moved on attachment remove/replace).
- On "AI Edit," if any region has strokes, they're converted to full-image fractional coordinates (2x3 grid math) and rasterized in one pass onto a copy of the full source image via new `MediaEditorUtil.rasterizeAnnotation()`, producing one overlay image.
- `editImageWithAi()` now takes `extraReferenceImages: List<Uri>` and sends them alongside the main image — reaches the model via Phase A's multi-image `editImage()` widening, so the markup actually arrives, not just a text description of where it is.
- Files touched: `AttachmentEditorDialog.kt`, `MediaEditorUtil.kt`, `ChatInputBar.kt`, `ChatViewModel.kt`, `ChatScreen.kt`.

**Phase B+ — Face swap, background swap confirmation, voice-over, video send-to-contact — DONE (2026-10-06)**
- **Face swap safeguard (the load-bearing design decision):** a face can only become a swap target by being captured live via the in-app camera at registration time (never a gallery pick) — `VerifiedFaceRepository.register()` enforces this and runs ML Kit face detection to confirm exactly one clear face. Stored device-local only (`verified_faces` Room table + `filesDir`, no backend sync). New Settings section "VERIFIED FACES (FACE SWAP)" to manage them.
  - This explicitly does NOT support swapping in a face from an arbitrary uploaded photo of someone who hasn't gone through that capture flow themselves (e.g. a celebrity photo) — raised directly by the user and declined: there's no reliable way to verify consent for an arbitrary photo, and the harm (non-consensual imagery, impersonation) happens at generation time, before any downstream safeguard (watermark, metadata) could matter. Multi-person swaps are fully supported as long as every included face went through its own live registration.
  - Routing: images — the picked face's image rides as an extra reference image through the already-multi-image `editImage()` (Phase A/B), with an auto-appended instruction. Video — passed with `explicitImageRoles = ["character"]` so the backend's `referenceImageBase64` extraction (Phase A) picks it up for the BYOK video-edit path. No new backend code needed for either — this proved to be almost entirely a client-side safeguard + UX layer on top of what Phase A/B already built.
  - Output tagging: `ChatMessage.provider` gets a `:face-swap` suffix; `ChatBubble.kt` renders a visible "AI FACE-EDITED" badge whenever present.
- **Background swap confirmed working as-is**: already worked via a plain text AI-edit instruction (no identity/consent concern, nothing changed).
- **Voice-over**: new "Voice-Over" tool in the video attachment editor — `VoiceManager.synthesizeToFile()` (TTS) + new `MediaEditorUtil.addAudioToVideo()` (Media3 Transformer, two parallel `EditedMediaItemSequence`s composited together) replaces or layers narration onto a video.
- **Send video to contact**: `ChatBubble.kt`'s existing "Send to Contact" menu item was gated to `message.type == "image"` even though the underlying `sendImageToContact`/backend media-hosting pipeline was already fully media-type-agnostic — extended the gate to include `"video"`, no other changes needed.
- Files touched: `VerifiedFaceEntity.kt`, `VerifiedFaceDao.kt`, `VerifiedFaceRepository.kt`, `VerifiedFacesSection.kt`, `MistrealDatabase.kt`, `AppModule.kt`, `MediaEditorUtil.kt`, `AttachmentEditorDialog.kt`, `ChatInputBar.kt`, `ChatViewModel.kt`, `ChatScreen.kt`, `ChatBubble.kt`, `SendMessageUseCase.kt`, `SettingsViewModel.kt`, `SettingsScreen.kt`.
- **Not yet verified on-device** — the test device disconnected partway through this session and hasn't reconnected; everything above compiles clean but hasn't been run.

**Multi-face video targeting — DONE (2026-10-06)**
- `ChatViewModel.detectFacesInVideo()` extracts the video's middle frame and runs ML Kit face detection (reusing `FaceGuard`), returning the frame + each face's bounding box as a 0f..1f fraction.
- New "Multiple people? Pick which face to swap" step in the video AI Edit dialog, shown once a verified face is picked: renders the frame with tappable boxes over each detected face (yellow, green when selected).
- On confirm, `MediaEditorUtil.rasterizeFaceTargetMarker()` draws a highlight box onto a copy of that frame, saved and sent as a 4th image role (`face_target`), alongside the verified face (`character`) — required a new field threaded through `aiService.ts`/`VideoEditRequest`/`VideoEditProvider` (`faceTargetImageBase64`) since the existing 3 roles (start/end/character) had no slot for "which existing face in the scene to target," distinct from "which face to insert."
- Files touched (new): `ChatViewModel.kt` (`detectFacesInVideo`, `markFaceTarget`), `MediaEditorUtil.kt` (`rasterizeFaceTargetMarker`), `AttachmentEditorDialog.kt`, `ChatInputBar.kt`, `ChatScreen.kt`, `aiService.ts`, `videoEditProvider.ts`.

**Audit + logging pass — DONE (2026-10-06), requested directly by the user ("let it log it all")**
- Found and fixed 8 completely silent `catch (e) {}` blocks across the backend (`index.ts`, `aiService.ts` ×1, `intelligenceService.ts` ×5, `astroService.ts` ×1) — all now log via the existing `logger` utility, which already reaches Render's log stream (no new logging infra needed, it already existed and already ships there — this was purely about using it everywhere a failure could hide).
- Found and fixed 2 genuinely silent failure paths in the NEW market code: `getQuote()`/`getCandles()` in `marketDataService.ts` returned `null` with zero logging on a missing API key or unmapped crypto symbol — now every path logs specifically why. Added a "requested N, got M, missing: [...]" summary line to the `/watchlist` route — this is the exact "we send 5, only 3 got there" visibility the user asked for.
- `MarketAlertWorker.kt`: a failed fetch previously returned `Result.success()` silently (WorkManager would never retry); now logs and returns `Result.retry()`. Each delivery/acknowledge step now logs too.
- Added Timber logging throughout the newest client repositories/ViewModels (`MarketRepository`, `VerifiedFaceRepository`, `InfoRepository.getBankChannels`, `editImageWithAi`/`editVideoWithAi`/`addVoiceOverToVideo`/`detectFacesInVideo` in `ChatViewModel`) — every success and failure path now has a trace, matching the existing `Timber.w(e, "...")` convention already used elsewhere in the codebase (e.g. `BusinessRepository`).
- Fixed an inaccurate doc comment on `VerifiedFaceRepository`: it previously implied the face image is "never synced to the backend," which isn't true — it's sent to the backend/BYOK provider whenever actually used in an edit (that's unavoidable, it's how the swap happens); corrected to say the *registry* itself never syncs, not the image-in-use.
- Added a small UI gap fix: `MarketAlertsSection`'s watchlist silently rendered nothing at all when empty, with no indication why — now shows "Markets temporarily unavailable."
- No disconnected/placeholder wiring found beyond what's listed above — the two load-bearing checks (did every AttachmentEditorDialog callback signature actually get updated end-to-end across ChatInputBar → ChatScreen → ChatViewModel each time a param was added, and does every new backend field actually get read by something) were traced by hand for each change made this session.
- **Still not verified on-device** — the device has been disconnected for the entire remainder of this session.

**Phase C — NOT STARTED, needs a decision before building**
Per-scene video annotation + tracking + final multi-scene assembly. Two real open items from the original plan still unresolved:
1. Durable state model — ephemeral (lost on dialog close, like today's segment notes) or a persisted project you can return to across sessions (needs new Room tables)?
2. Final assembly into one video is genuinely new work no matter what — Media3 Transformer is only used for trim today, not multi-clip composition; this needs either a local Transformer-based concatenation step or an external provider that accepts ordered multi-scene input. Worth scoping/estimating on its own before committing to it.

**Phase A (original scope) — Keyframe reference inputs for single-shot generation/reconstruction**
- Builds directly on `AttachmentEditorDialog.kt`'s `SegmentEditor`/`segmentNotes`, per §2/§3 above.
- Needs per-attachment edit history (not global chat history) for genuine iterative refinement.
- **Achievable now** using Gemini 2.5 Flash Image's multi-image input, once Phase A1's `editImage()` widening lands — no new provider required specifically for this phase.

**Phase C — Extend to per-scene video annotation with tracking**
- Reuses Phase B's pencil+text tool on a representative frame per temporal segment (the video `SegmentEditor` already extracts per-segment thumbnails).
- Needs the new `SceneDescriptor` state (§3) persisted across the editing session — today's `segmentNotes` is a transient `Map` scoped to one dialog/attachment session; scaling to scene-by-scene tracking with draft results and revision history is new, durable-feeling state (open question: does it need to survive app restart / Room persistence, or is in-memory ViewModel state sufficient?).
- Per-scene "first-draft reconstruction": extract each scene via `MediaEditorUtil.trimVideo` and run it through the existing AI-edit flow independently, storing the result against its `SceneDescriptor`.
- Final assembly into one video: **genuinely new work** regardless of AI provider choice — Media3 Transformer is only used for trim today, not concatenation; this needs either a local Transformer-based multi-clip composition step or an external provider that accepts ordered multi-scene input.
- **Partially achievable now** (per-scene drafting); **concatenation/assembly is new work** that should be scoped and estimated separately before committing to a ship date.

## 5. Settings Placement

No dropdown/expandable-section UI pattern exists anywhere in `SettingsScreen.kt` today — `ByokSettingsSection`, `ByokVideoSettingsSection`, and the two `MediaGenProviderSection(viewModel, "image_gen"/"video_gen", ...)` calls are all flat, stacked `SettingsSection` cards. Two options to put in front of the user (don't decide unilaterally — see Open Questions):

- **Option 1 (consistent with existing pattern, lower risk):** add one more stacked card alongside the two existing `MediaGenProviderSection` calls, since keyframe/character-reference inputs are really a configuration knob on top of the image-gen/video-gen provider choice that already exists, not a separate capability domain.
- **Option 2 (new pattern):** if annotation/scene-tracking grows enough sub-toggles to warrant it, introduce a genuinely new, reusable `ExpandableSettingsSection`/dropdown component — but build it as shared infrastructure, not bespoke to this one feature, since nothing like it exists yet. (Note: this would land in the same place as the already-planned Settings accordion redesign — worth doing together, not twice.)

## 6. Open Questions for the User

1. **Settings placement** — flat section (Option 1) or new dropdown/expandable pattern (Option 2)? See §5.
2. **Extend `AttachmentEditorDialog.kt` vs. a separate editor?** Recommend extension given how much is reusable (fractional-coordinate drag math, segment fusion philosophy, thumbnail grid) — but pencil annotation + scene tracking is meaningfully bigger in scope than today's text-only notes, and might eventually want its own screen for discoverability/bitmap-memory reasons. Confirm the direction before Phase B starts.
3. **Is "Scene Mode" intentionally inert right now, or an unnoticed bug?** It currently appears broken at the transport layer (multer doesn't declare `start_frame`/`end_frame` fields) and likely errors when toggled on with images attached. Confirm this isn't already being tracked/fixed elsewhere before Phase A touches the same code paths.
4. **RESOLVED (2026-10-06, verified against ai.google.dev):** Veo **3.1** (not Veo 2, which is what's currently configured — `VIDEO_GEN_MODEL_ID = 'veo-2.0-generate-001'`) genuinely supports first/last-frame conditioning on the same Gemini API REST surface already in use (not Vertex AI, no new auth/billing model needed). Confirmed request shape:
   ```
   POST https://generativelanguage.googleapis.com/v1beta/models/veo-3.1-generate-preview:predictLongRunning
   {
     "instances": [{
       "prompt": "...",
       "image": {"inlineData": {"mimeType": "image/png", "data": "<base64>"}},
       "lastFrame": {"inlineData": {"mimeType": "image/png", "data": "<base64>"}}
     }],
     "parameters": {}
   }
   ```
   Same `inlineData.{mimeType,data}` shape the backend's existing `editImage()` Gemini call already uses — this is a natural extension of `generateVideo()`, not a new integration pattern. Implication for Phase A: bump `VIDEO_GEN_MODEL_ID` to `veo-3.1-generate-preview` and add `image`/`lastFrame` to `instances[0]` when start/end reference images are present. Note Veo 3.1 Lite does NOT support this — must stay on full 3.1 or 3.1 Fast. Sources: [Generate videos with Veo 3.1 in Gemini API](https://ai.google.dev/gemini-api/docs/veo), [Introducing Veo 3.1 and new creative capabilities](https://developers.googleblog.com/introducing-veo-3-1-and-new-creative-capabilities-in-the-gemini-api/).

   The BYOK path (Phase A's other leg, for users on a different provider) remains equally valid and should still be built — per the user's explicit instruction, the feature should work "if they use another AI which is in the custom API," i.e. BYOK users whose own configured video provider supports start/end-frame conditioning get it through the generic adapter regardless of what the app's own Veo integration supports.
5. **Is a new/different paid provider acceptable** for genuine start/end/character-conditioned **video** generation, given Veo's uncertain support on the currently-integrated API surface? Decide between (a) accepting Veo as text-only for now and gating true keyframe video behind BYOK, or (b) investing in a Vertex AI Veo integration.
6. **How durable does per-scene "back and forth" state need to be?** Ephemeral, single-session tool like today's `segmentNotes` (lost if the user closes the dialog), or a durable project revisited over days/sessions (needing new Room persistence)? Nothing today persists beyond the current pending-attachment session.
7. **Is the character reference (image + description) a reusable, named asset** the user can save and recall across multiple future messages/scenes ("use this character consistently"), or strictly scoped to one message's attachments? No asset-library concept exists in the codebase today; the data model in §3 would need a save/reuse layer if the answer is yes — better to decide before Phase A ships to avoid rework.

---

### Critical Files for Implementation
- `backend/src/services/aiService.ts`
- `backend/src/index.ts`
- `backend/src/services/ai/videoEditProvider.ts`
- `app/src/main/java/com/example/mistreal_mini/ui/chat/components/AttachmentEditorDialog.kt`
- `app/src/main/java/com/example/mistreal_mini/ui/chat/ChatViewModel.kt`
- `app/src/main/java/com/example/mistreal_mini/domain/usecase/SendMessageUseCase.kt`
- `app/src/main/java/com/example/mistreal_mini/ui/chat/components/ChatInputBar.kt`
