# Implementation Plan: Market Data Feed, Lightweight Candlestick Charts, and Bank Official-Channel Linking

*Plan date: 2026-10-06.*

## Codebase findings that shape this plan

- `backend/src/services/intelligenceService.ts` is a buffer-based engine: each category (`news`, `astro`, `novels`, `wiki`, `journals`, `sports`) is stored in a shared `IntelligenceBuffer` Postgres table (`category` STRING unique, `items` JSONB, `lastUpdated`), refreshed by `setInterval` workers in `backend/src/index.ts` (hourly global refresh, 10-min sports refresh, 30-min weather refresh), and merged by `getInterleavedFeed()`. New items use `updateBuffer()` (append + dedupe-by-title + age-prune) or `replaceBuffer()` (wholesale replace, used by sports because the same title's score changes over time) — **market prices need `replaceBuffer()` semantics**, exactly like sports, since "AAPL $230" today and "AAPL $228" tomorrow is the same title with different content, not a new item.
- Optional external APIs already follow a fail-soft pattern: `process.env.NEWS_API_KEY` (really `NEWSAPI_KEY` in `.env.example` — note the existing naming inconsistency, don't repeat it), `SPORTSDB_API_KEY` defaulting to a public test key `'3'`, `OPENWEATHER_API_KEY` returning an "unavailable" stub when absent. New market-data keys must follow this: absent key → feed category simply doesn't populate, never a crash.
- Client-side, `app/src/main/java/com/example/mistreal_mini/ui/dashboard/components/FeedComponents.kt`'s `IntelligenceFeedView` renders `novel/wiki/journal` as one shared horizontal "RESEARCH & LITERATURE" `LazyRow` of `IntelMiniCard`, and `sports` as its own "LIVE SCORES" `LazyRow`, both gated by `selectedCategory` filter chips (`ALL/NEWS/ORBITAL/RESEARCH`). A `markets` type would add a fourth horizontal row following this exact template.
- `Article` (in `InfoApiService.kt`) is currently flat: `title, description, url, type, isPinned, category, timestamp` — no structured numeric fields. Market items need either a `metadata: Map<String, Any>` addition (there is already prior art for this shape — `PinIntelRequest.metadata` is `Map<String, Any>?`) or encoding price/change/currency into `description`/`category` strings. Given the chart feature needs structured numbers (symbol, exchange, currency) for a follow-up API call, **adding an optional `metadata` map to `Article` is the cleaner route**, not string-parsing.
- `LocationHelper.kt` (`app/src/main/java/com/example/mistreal_mini/util/LocationHelper.kt`) already wraps `android.location.Geocoder` for reverse geocoding (`getCityName`) and is already injected into `GetIntelligenceFeedUseCase`, which already sends `lat/lon` to the backend for weather (`infoRepository.getWeather(lat, lon)`). This is the natural integration point for location → currency, no new permission or plumbing needed.
- The backend already independently receives lat/lon per user and calls OpenWeatherMap, whose response includes a country code (`weather.sys.country`, an ISO 3166-1 alpha-2) that `weatherService.ts` currently discards. **This is a nearly-free signal** — capturing it avoids a second reverse-geocoding call just to learn the country.
- `User` model already persists `lastKnownLat/lastKnownLon/lastKnownCity/lastWeatherSummary` and is refreshed every 30 minutes by `refreshProactiveWeather()` — the same place to cache a derived `lastKnownCurrency` (ISO 4217) would require zero new infrastructure.
- Facebook Messenger deep-link precedent lives in `app/src/main/java/com/example/mistreal_mini/ui/chat/ChatScreen.kt` (~line 282-299): try `Intent(ACTION_VIEW, Uri.parse("fb-messenger://user-thread/${contact.id}"))`, catch `ActivityNotFoundException`, fall back to `https://m.me/${contact.id}`. WhatsApp's equivalent is `https://wa.me/<number>` (optionally `?text=` to prefill a message) — WhatsApp doesn't have a distinct native-app URI scheme the way Messenger does; `wa.me` itself redirects into the installed app if present, into WhatsApp Web/Play Store if not. So the WhatsApp side of this feature is actually simpler than the Messenger one already in the codebase, not harder.
- There is already a `BankDao`/`BankLinkEntity`/`MultiLinkDialog.kt` "Add Financial Portal" feature in Settings — but it is a **user-curated generic bookmark launcher** (user types a name/URL/optional package ID; tapping either launches that package or opens the URL in a browser; explicitly closes the app immediately "for security"). It has nothing to do with WhatsApp/Facebook or official channels, and nothing to do with `SocialPlatformResponse`/OAuth. It's relevant only as a UI precedent ("Settings has a banking-adjacent entry point already") and as something to avoid colliding with — Part 3 should be a new, separate, curated feature, not bolted onto this generic link-launcher.
- `backend/src/services/socialPlatforms/platformRegistry.ts` has a clean precedent for exactly the "which capabilities does X support" problem Part 3 needs: a typed `SocialPlatformCapabilities` interface (`supportsFeed/supportsDM/...`) attached to each platform definition. The bank-channel-support design below reuses this shape (`supportsWhatsApp: boolean, supportsFacebook: boolean`) per bank entry.
- Confirmed via live search that this per-bank variance is real, not hypothetical: UBA supports WhatsApp + Facebook + Messenger for its "Leo" chatbot; Access Bank, per current public info, does not offer WhatsApp banking at all; Zenith Bank's WhatsApp bot "ZiVA" is a dedicated number (`+234 704 000 4422`) distinct from its Facebook page. Confirms the "not every bank supports both" framing is correct and that a per-bank, per-channel flag is necessary, not a blanket toggle.

---

## Part 1 — Market data feed items

### Data source feasibility

| Need | Candidate | Free tier (verified Oct 2026) | Verdict |
|---|---|---|---|
| Stocks | Twelve Data | 800 calls/day, 8/min, 5,000 datapoints/req, covers stocks+forex+ETFs+commodities+crypto in one API | **Primary recommendation** — one provider covers stocks AND commodities AND forex AND crypto, minimizing integrations |
| Stocks (backup) | Finnhub | ~60 calls/min, free for non-commercial use, stocks+forex+crypto | Backup/alternative if Twelve Data's 800/day is too tight once multiplied across many users' watchlists |
| Stocks (backup) | Alpha Vantage | Only 25 req/day, 5/min, free `outputsize=compact` caps at 100 points/request | Too thin for a shared server-side cache refreshed across many symbols; keep as last-resort fallback only, same role `SPORTSDB_API_KEY` plays as a fallback key |
| Commodities/food prices | Twelve Data (covers many), or API Ninjas Commodity Price API (free tier = daily settlement price, not live) | Free, but commodity coverage/depth is thinner than equities across all providers | Use Twelve Data's commodity symbols as primary; treat commodities as "settle-price, once-daily" data, don't pretend it's live |
| Currency conversion | **Frankfurter** (`api.frankfurter.dev`) | No API key, unlimited free calls, 223 currencies from ECB/central banks, daily rates back to 1948 | **Clear winner** — no key needed at all, matches the "fails soft when env var absent" pattern trivially since there's no env var to be absent |
| Crypto | **CoinGecko** | Free Demo plan: 100 calls/min, 10,000 calls/month, no key needed for the keyless public endpoint (~10-30 calls/min); OHLC daily endpoint supports up to 180 days per request | **Matches the "max 6 months" requirement almost exactly** (180 days ≈ 6 months) — the API's own free-tier cap aligns with the product requirement, which is a good sign this is the right provider |

Given the above, the server-side design uses two keyed integrations (Twelve Data for stocks/commodities, gated by `process.env.TWELVE_DATA_API_KEY`) plus two keyless ones (Frankfurter for forex, CoinGecko for crypto) — following the exact `NEWS_API_KEY`-present-or-feed-is-empty pattern already in `intelligenceService.ts`.

### Backend design

New `backend/src/services/marketDataService.ts` (parallel to `weatherService.ts`/`astroService.ts`):
- `refreshMarkets()` — called from `IntelligenceService.refreshGlobalIntel()` alongside the existing five refreshers, but on its own tighter interval registered in `index.ts` (prices move faster than news; something like every 15-30 min given Twelve Data's 800/day budget — a watchlist of ~15 symbols refreshed every 20 min is ~1,080 calls/day for batch-fetchable symbols, so batch the `/quote` endpoint with multiple symbols per call, not one call per symbol, to stay inside budget).
- Fetch a small curated default watchlist (a handful of major indices/stocks, a couple of commodities like gold/oil, BTC/ETH) server-side into a shared `markets` `IntelligenceBuffer`, using `replaceBuffer('markets', items)` — same reasoning as sports: today's AAPL price replaces yesterday's under the same title, it's not a "new item to append."
- Each buffer item carries: `{ title: "[Markets] AAPL", description: "$230.12 (+1.2%)", type: 'markets', symbol, assetClass: 'stock'|'crypto'|'commodity', priceUsd, changePercent, currency: 'USD', timestamp }`.
- Currency conversion: do NOT bake a single hardcoded local currency into the shared buffer (that buffer is server-wide, shared across all users with different locales) — store canonical USD price in the buffer, and apply the conversion **at feed-serving time** in `getInterleavedFeed()`, using the requesting user's cached `lastKnownCurrency` (see below) and a short-lived in-memory or Redis-less DB cache of Frankfurter rates (rates only need refreshing once or twice a day, Frankfurter is free/unlimited so even per-request calls are fine, but caching avoids unnecessary latency).
- `GET /intelligence/feed` (wherever that route lives) already resolves `user` by `firebaseUid` — reuse that same lookup to read `user.lastKnownCurrency` and attach a `localPrice`/`localCurrency` field onto each `markets` item before returning.

### Location → local currency mapping

- Capture `weather.sys.country` (ISO 3166-1 alpha-2, e.g. `"NG"`) inside `getWeatherData()` in `weatherService.ts` — it's already in the OpenWeather response and currently thrown away.
- Add a small static `countryCodeToCurrency` lookup table (ISO 3166-1 alpha-2 → ISO 4217, e.g. `{ NG: 'NGN', GB: 'GBP', US: 'USD', ... }`) — this is a bounded, essentially-static dataset (~250 entries), no npm package needed, can live as a plain TS object, analogous in spirit to the static `SocialPlatformDefinition` list in `platformRegistry.ts`.
- `refreshProactiveWeather()` (already runs every 30 min per active user) is the natural place to also set `user.lastKnownCurrency = countryCodeToCurrency[countryCode] ?? 'USD'`, alongside its existing `lastKnownCity`/`lastWeatherSummary` writes — no new worker needed, piggyback on the existing one.
- Client-side alternative/fallback: if no server-cached currency yet (new user, location never reported), `LocationHelper.getCityName()`'s `Address` object from `Geocoder.getFromLocation()` also exposes `address.countryCode` directly — the client could derive currency locally via `java.util.Currency.getInstance(Locale("", countryCode))` and send it up as a header/param, giving a second path that doesn't even need the backend's lookup table. Worth deciding which path is primary (see Open Questions).

### Client design

- Extend `Article` with an optional `metadata: Map<String, Any>?` (consistent with `PinIntelRequest.metadata`'s existing shape) to carry `symbol`, `assetClass`, `localPrice`, `localCurrency`, `changePercent`.
- In `FeedComponents.kt`, add a `"MARKETS"` horizontal row following the exact `LazyRow` + `IntelMiniCard`-style template already used for `sports`/`novel|wiki|journal`, filtered by `article.type == "markets"`, placed next to "LIVE SCORES". Each card shows symbol, local-currency price, and a colored up/down change indicator (green/red), tapping into Part 2's detail/chart screen.
- Extend the `selectedCategory` filter chips (`ALL/NEWS/ORBITAL/RESEARCH`) with a `"MARKETS"` option, same pattern as the existing four.

---

## Part 2 — Lightweight candlestick charts

### Charting approach

- `gradle/libs.versions.toml` has **no charting library dependency today** (checked `app/build.gradle.kts` and the version catalog — nothing matching chart/graph/vico/mpandroidchart).
- 6 months of daily candles ≈ 126-130 trading days (fewer for crypto/commodities which may trade on weekends, so up to ~180 points, matching CoinGecko's own 180-day OHLC cap). This is a genuinely tiny dataset — **custom `Canvas`-drawn candlesticks in Compose, with no third-party dependency, is not just adequate but the better fit for the user's own "keep it minimal" instruction.** A ~130-point static line/candle render is well within what `Canvas.drawLine`/`drawRect` can do with zero per-frame cost once drawn (Compose only re-draws on recomposition, not continuously).
- Recommendation: **do not add a charting library.** Build a `CandlestickChart` composable using `androidx.compose.foundation.Canvas` (already available transitively via Compose Foundation, already a dependency) that:
  - Takes a `List<Candle>` (open/high/low/close/timestamp) and draws green/red rectangles for body + thin lines for wick, scaled to the Canvas size.
  - No zoom/pan/crosshair/tooltips initially — genuinely minimal, matching "no need for anything... minimal but detail." A tap-to-select-a-candle-and-show-its-OHLC-in-a-small-label is a reasonable v1.1 addition, not v1.
- This same composable serves stocks, crypto, and commodities uniformly — the user's own framing ("no need for anything even for crypto currency and commodity") means **one chart component, one data shape (`List<Candle>`), three data sources feeding into it** — exactly matching the plan's symmetry goal. Don't build per-asset-class chart variants.

### Where it lives / data flow

- Tapping a `markets`-type `IntelMiniCard` (or the new detail popup, following the `IntelDetailPopup` pattern already used for other feed types) opens a new `MarketDetailScreen` showing the symbol, current local price, and the `CandlestickChart`.
- New backend endpoint, e.g. `GET /markets/:symbol/candles?assetClass=stock|crypto|commodity&range=6m`, dispatching to Twelve Data's `/time_series?interval=1day&outputsize=180` for stocks/commodities or CoinGecko's `/coins/{id}/ohlc?days=180` for crypto — same shape response either way (`[{timestamp, open, high, low, close}]`) so the client's `CandlestickChart` never needs to know which provider served it.
- Caching: daily candles don't change until the next day's close, so cache server-side per-symbol-per-day (a cheap `MarketCandleCache` table or even reuse the existing `IntelligenceBuffer` JSONB pattern with `category: 'candles_AAPL'`) and cache client-side in Room (the app already depends on `androidx.room:2.6.1`) keyed by symbol+date, so reopening the same stock's chart later the same day is instant and makes zero network calls — directly fulfilling "not re-fetched on every view."

---

## Part 3 — Bank official-channel linking

### Payment-processing boundary (explicit, load-bearing)

**This feature does exactly one thing: open a chat thread with a bank's own official WhatsApp Business number or Facebook Page, using the same style of OS-level deep link already in `ChatScreen.kt` for Messenger.** The app never collects, stores, transmits, or touches any banking credential, account number, or payment instruction. Any "payment done through chat" happens entirely inside WhatsApp/Facebook and the bank's own backend, completely outside this app's code, servers, or data model. This boundary must be reflected in the UI copy itself (e.g. a line like "You're leaving the app to chat directly with [Bank]'s official channel — Mistreal doesn't process payments or see your messages") so it can never be mistaken later for a payment feature to build toward.

### Directory feasibility (the honest risk)

- Confirmed via search: there is no clean global API or dataset mapping "bank name" → "official WhatsApp number" / "official Facebook Page ID." What exists is scattered, per-country journalism/listicle content (e.g. Nigerian-finance blogs listing GTBank/UBA/Zenith/FirstBank WhatsApp numbers) and vendor marketing pages (Meta Business Solution Providers like Infobip/Gupshup/CEQUENS serve banks but don't publish a public directory of their client banks' numbers).
- Real, verified examples found: Zenith Bank's WhatsApp bot "ZiVA" is reachable at `+234 704 000 4422`; UBA's "Leo" chatbot is explicitly available on **WhatsApp, Facebook, and Messenger**; First Bank Nigeria's WhatsApp banking is `0812 444 4000`; GTBank supports WhatsApp Business; Access Bank, per currently available public info, does **not** offer WhatsApp chat banking (illustrating exactly why a per-bank capability flag is required, not a global assumption that every bank has both channels).
- **Conclusion: this must start as a curated, manually-researched static list**, expandable incrementally — there is no live-lookup API to build against. This is a data-entry/maintenance cost, not a code complexity cost, and should be scoped and communicated to the user as such.

### Design

- New static/seed dataset (backend-owned, served to the client — easier to update server-side without an app release than if it were bundled client-side), shaped like `platformRegistry.ts`'s `SocialPlatformDefinition`:
  ```
  interface BankChannelDefinition {
    id: string;            // "zenith_ng"
    displayName: string;   // "Zenith Bank"
    country: string;       // "NG"
    whatsapp?: { number: string; prefilledMessage?: string };
    facebook?: { pageId: string };
  }
  ```
- Client: `GET /banks/channels?country=NG` (or filtered by the user's detected country, reusing the same reverse-geocoded country code from Part 1) returns the list; user picks their bank from a searchable list (reasonable UI: a new screen, not jammed into the existing generic `BankDao` "Add Financial Portal" bookmark list, since that's a different, user-curated feature — see findings above).
- Deep-link resolution logic (client-side, mirroring `ChatScreen.kt`'s existing try/catch pattern exactly):
  1. If the selected bank has `whatsapp` AND WhatsApp is installed (`context.packageManager.getLaunchIntentForPackage("com.whatsapp")` or `com.whatsapp.w4b` for Business app, non-null) → launch `https://wa.me/<number>?text=<prefilled>`.
  2. Else if the bank has `facebook` AND Messenger/Facebook is installed → try `fb-messenger://user-thread/<pageId>` falling back to `https://m.me/<pageId>` (identical try/catch already proven in `ChatScreen.kt`).
  3. Else if the bank supports a channel but the corresponding app isn't installed → `wa.me`/`m.me` URLs still work as web fallbacks (WhatsApp Web, Facebook mobile web), so there's always a usable path, just a less native one.
  4. If the bank supports neither channel in the dataset → surface it honestly in the UI ("No official chat channel on file for this bank yet") rather than guessing.
- Entry point: given the existing generic bank-bookmark feature already sits in Settings (`MultiLinkDialog.kt`'s "Add Financial Portal"), the cleanest placement for this *new*, distinct feature is either (a) its own "Contact Your Bank" card alongside it in that same dialog/section (visually adjacent, functionally separate), or (b) surfaced contextually from the `markets` feed/dashboard. Recommend (a) for discoverability without feed clutter — flagged as an open question below.

---

## Phasing

**Phase 1 — Market data feed (read-only, no chart yet)**
- Backend: `marketDataService.ts`, Twelve Data + CoinGecko integration, `markets` `IntelligenceBuffer` with `replaceBuffer`, country→currency capture off the existing weather call, Frankfurter conversion applied at feed-serving time.
- Client: `Article.metadata` field, `"MARKETS"` horizontal row in `FeedComponents.kt`, filter chip.
- Ships independently; no dependency on Parts 2 or 3.

**Phase 2 — Candlestick detail view**
- Backend: `/markets/:symbol/candles` endpoint with server-side daily cache.
- Client: `CandlestickChart` Canvas composable, `MarketDetailScreen`, Room-backed client cache.
- Depends on Phase 1's feed items existing as the tap-in entry point, but the chart component and endpoint can be built in parallel once the `Candle` data shape is agreed.

**Phase 3 — Bank official-channel linking**
- Fully independent of Phases 1-2. Backend: `BankChannelDefinition` seed data + `/banks/channels` endpoint. Client: bank picker screen, WhatsApp/Facebook deep-link resolver, UI copy stating the payment boundary.
- Start with a single country's major banks (Nigeria, given the Zenith Bank example) to validate the UX and data-maintenance workflow before expanding.

---

## Open questions — RESOLVED (2026-10-06)

1. **Market data scope — RESOLVED: both tiers, gated on the existing single Pro flag.** Everyone gets the free shared watchlist (the curated default set from Part 1). Per-user custom symbol tracking is a new Pro-gated capability — checked via the existing `isPro`/`PreferenceManager.isPro` flag already used everywhere else in the app (model drawer Free/Premium split, `SubscriptionScreen.kt`'s single "Agent Upgrade" tier). **Important correction during planning:** the user initially believed there were two premium tiers to choose from for this gate — verified against `SubscriptionScreen.kt`/`SubscriptionViewModel.kt`/`PreferenceManager.kt` and confirmed there is only one (`isPro: Boolean`, one price, one purchase flow). User confirmed: gate custom symbol tracking behind the existing single Pro tier, no new billing/IAP tier needed. (A genuine second, higher tier was explicitly declined as out of scope for this feature — if ever wanted, it needs its own separate planning pass covering Play Store product setup and `SubscriptionViewModel` billing flow changes.)
   - Design implication: `markets` buffer items split into two kinds — the free shared watchlist (server-curated, always populated) and per-user custom symbols (new, Pro-gated; needs a new user-owned symbol list, e.g. a `userWatchlist` table or a JSONB column on `User`, and its own refresh accounting separate from the shared watchlist's API budget since it scales with active Pro users rather than staying fixed).
2. **Currency conversion** — still open (label vs. toggle), not addressed in this round.
3. **Bank-channel directory geography — RESOLVED: location-match with a "world" fallback.** The bank picker should default to showing banks matching the user's detected country (reusing the same reverse-geocoded/weather-derived country code from Part 1) when location is available, and fall back to showing the full available list ("world" — i.e. every bank in the curated dataset regardless of country) when location isn't available or doesn't match anything in the dataset. This is a filtering/UX rule that should be built in from the start even though the underlying dataset itself still has to start small: **seed data collection still begins with Nigeria** (the only country with verified examples so far — Zenith, UBA, First Bank, GTBank, Access), since there's no bulk API to pull this from regardless of how many countries are targeted. The location-match-first design just means the UI won't need rework as more countries get added later — a Nigerian user today sees Nigerian banks by default; a user elsewhere today sees the "world" fallback (which, for now, is still just the Nigerian list, honestly labeled, until more countries are added).
4. **Bank-channel entry point placement** — still open, not addressed in this round.
5. **Candle chart range control** — still open, not addressed in this round.

---

### Critical Files for Implementation

- `backend/src/services/intelligenceService.ts`
- `backend/src/services/weatherService.ts`
- `backend/src/index.ts`
- `backend/src/services/socialPlatforms/platformRegistry.ts`
- `app/src/main/java/com/example/mistreal_mini/ui/dashboard/components/FeedComponents.kt`
- `app/src/main/java/com/example/mistreal_mini/data/api/InfoApiService.kt`
- `app/src/main/java/com/example/mistreal_mini/util/LocationHelper.kt`
- `app/src/main/java/com/example/mistreal_mini/ui/chat/ChatScreen.kt`
- `app/src/main/java/com/example/mistreal_mini/ui/settings/components/MultiLinkDialog.kt`
