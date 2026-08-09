# Social Media Integration - Deployment Checklist

## ✅ Code Components Status

### Backend Files Created
- ✅ `backend/src/services/socialPlatforms/index.ts` - Export barrel
- ✅ `backend/src/services/socialPlatforms/instagram.ts` - Instagram Graph API
- ✅ `backend/src/services/socialPlatforms/twitter.ts` - Twitter API v2
- ✅ `backend/src/services/socialPlatforms/facebook.ts` - Facebook Graph API
- ✅ `backend/src/services/socialPlatforms/whatsapp.ts` - WhatsApp Cloud API
- ✅ `backend/src/services/socialPlatforms/telegram.ts` - Telegram Bot API
- ✅ `backend/src/services/socialPlatforms/unified.ts` - Unified service (orchestrator)
- ✅ `backend/src/routes/socialRoutes.ts` - OAuth routes
- ✅ `backend/src/models/SocialToken.ts` - Database model
- ✅ `backend/src/models/index.ts` - Model exports
- ✅ `backend/src/index.ts` - Updated with social routes

### Android Files Created
- ✅ `app/.../data/models/SocialPost.kt` - Data models
- ✅ `app/.../data/api/SocialApiService.kt` - Retrofit interface
- ✅ `app/.../data/repository/SocialRepository.kt` - Repository layer
- ✅ `app/.../domain/usecase/GetSocialPostsUseCase.kt` - Use cases
- ✅ `app/.../ui/components/SocialPostCard.kt` - Post card UI

### Configuration Files
- ✅ `backend/.env.example` - Environment template
- ✅ `.env.example` - Root env template (alternate location)

### Documentation Files
- ✅ `SOCIAL_INTEGRATION_GUIDE.md` - Comprehensive guide
- ✅ `SOCIAL_IMPLEMENTATION_STATUS.md` - Status & next steps
- ✅ `SOCIAL_SETUP.sh` - Setup helper script

---

## 🔧 Pre-Deployment Setup

### 1. Create Platform Developer Accounts

#### Instagram
- [ ] Go to https://developers.facebook.com/
- [ ] Create Meta Developer account (if needed)
- [ ] Create new app → Business type
- [ ] Add "Instagram Graph API" product
- [ ] Note the **Client ID** and **Client Secret**
- [ ] Add App Role: App Admin (your account)
- [ ] Create User Access Token with scopes: `instagram_basic,instagram_graph_user_profile,pages_read_engagement`

#### Twitter
- [ ] Go to https://developer.twitter.com/dashboard
- [ ] Create Twitter Developer account (if needed)
- [ ] Create new OAuth 2.0 app
- [ ] Set "User Context Permissions" to Read
- [ ] Note the **Client ID** and **Client Secret**
- [ ] Add callback URL to authorized URLs

#### Facebook
- [ ] Use SAME Meta Developer account as Instagram
- [ ] Go to your app dashboard
- [ ] Add "Facebook Login" product
- [ ] In Facebook Login → Settings:
  - [ ] Add Valid OAuth Redirect URIs
  - [ ] Enable "Embedded Browser OAuth Login"
- [ ] Note **App Secret** (same app as Instagram)

#### WhatsApp
- [ ] Go to https://www.whatsapp.com/business/
- [ ] Create WhatsApp Business account
- [ ] Verify phone number
- [ ] Get **Phone Number ID** from app dashboard (displayed as PHONE_NUMBER_ID)
- [ ] Add Webhook URL: `https://yourdomain/api/social/whatsapp/webhook`
- [ ] Generate verification token (random string, save it)

#### Telegram
- [ ] Open Telegram app
- [ ] Search for @BotFather bot
- [ ] Send command: `/newbot`
- [ ] Follow prompts to create new bot
- [ ] BotFather will send you a **bot token** (looks like `123456:ABC-DEF...`)
- [ ] (Optional) Send `/setwebhook` to set webhook URL

### 2. Prepare Render Deployment

- [ ] Note your domain (e.g., `mistreal-backend.onrender.com`)
- [ ] Ensure all redirect URIs use this domain:
  - Instagram: `https://mistreal-backend.onrender.com/api/social/callback/instagram`
  - Twitter: `https://mistreal-backend.onrender.com/api/social/callback/twitter`
  - Facebook: `https://mistreal-backend.onrender.com/api/social/callback/facebook`
  - WhatsApp: `https://mistreal-backend.onrender.com/api/social/whatsapp/webhook`
  - Telegram: `https://mistreal-backend.onrender.com/api/social/telegram/webhook`

- [ ] Update environment variables in Render dashboard:
  ```
  INSTAGRAM_CLIENT_ID=xxx
  INSTAGRAM_CLIENT_SECRET=xxx
  TWITTER_CLIENT_ID=xxx
  TWITTER_CLIENT_SECRET=xxx
  FACEBOOK_APP_ID=xxx
  FACEBOOK_APP_SECRET=xxx
  WHATSAPP_PHONE_NUMBER_ID=xxx
  TELEGRAM_BOT_TOKEN=xxx
  (other existing vars...)
  ```

### 3. Android Configuration

- [ ] Update `SocialApiService` base URL to Render domain:
  ```kotlin
  @Singleton
  @Provides
  fun provideSocialApiService(httpClient: OkHttpClient): SocialApiService {
      return Retrofit.Builder()
          .baseUrl("https://mistreal-backend.onrender.com/api/social/")
          .addConverterFactory(Json.asConverterFactory("application/json".toMediaType()))
          .client(httpClient)
          .build()
          .create(SocialApiService::class.java)
  }
  ```

- [ ] Add deep link intent filter to AndroidManifest.xml (in `<activity>` for MainActivity):
  ```xml
  <intent-filter>
      <action android:name="android.intent.action.VIEW" />
      <category android:name="android.intent.category.DEFAULT" />
      <category android:name="android.intent.category.BROWSABLE" />
      
      <data
          android:scheme="mistreal"
          android:host="social-connected"
          android:pathPrefix="/?" />
  </intent-filter>
  ```

- [ ] Add deep link handler to MainActivity.kt:
  ```kotlin
  override fun onCreate(savedInstanceState: Bundle?) {
      super.onCreate(savedInstanceState)
      
      val uri = intent.data
      if (uri?.scheme == "mistreal" && uri.host == "social-connected") {
          val platform = uri.getQueryParameter("platform") ?: ""
          val success = uri.getQueryParameter("success")?.toBoolean() ?: false
          val deviceId = uri.getQueryParameter("deviceId") ?: ""
          
          if (success) {
              // Trigger sync
              showSnackbar("✅ $platform connected successfully!")
              dashboardViewModel.syncSocialPosts(deviceId)
          } else {
              val error = uri.getQueryParameter("error") ?: "Unknown error"
              showSnackbar("❌ $platform connection failed: $error")
          }
      }
  }
  ```

- [ ] Wire up Settings screen connect buttons
- [ ] Wire up Dashboard to display social posts

---

## 🧪 Testing Checklist

### Backend Testing (Local)

```bash
# 1. Start backend
cd backend
npm run dev

# 2. Test OAuth URL generation
curl "http://localhost:3000/api/social/auth/instagram?deviceId=test123"
curl "http://localhost:3000/api/social/auth/twitter?deviceId=test123"

# 3. Test sync (after manual OAuth with real account)
curl -X POST http://localhost:3000/api/social/sync \
  -H "Content-Type: application/json" \
  -d '{"deviceId":"test123"}'

# 4. Test disconnect
curl -X POST http://localhost:3000/api/social/disconnect/instagram \
  -H "Content-Type: application/json" \
  -d '{"deviceId":"test123"}'
```

### Android Testing

- [ ] **Instagram OAuth Flow**
  - [ ] Click "Connect Instagram" button
  - [ ] Browser opens with Instagram login
  - [ ] Authorize app access
  - [ ] Redirected to app with success notification
  - [ ] 10 seconds later, Instagram posts appear

- [ ] **Twitter OAuth Flow**
  - [ ] Repeat above for Twitter
  - [ ] Tweets appear with correct formatting

- [ ] **Facebook OAuth Flow**
  - [ ] Repeat above for Facebook
  - [ ] Feed posts appear

- [ ] **WhatsApp (Limited)**
  - [ ] OAuth completes if Business account set up
  - [ ] Shows notification but limited data (requires webhook)

- [ ] **Telegram (Limited)**
  - [ ] Shows "Not connected" if webhook not set
  - [ ] After bot creates message, updates appear

- [ ] **Post Display**
  - [ ] Platform icon displays correctly
  - [ ] Author name shows
  - [ ] Timestamp shows relative time
  - [ ] Post content displays with text wrapping
  - [ ] Images load (if available)
  - [ ] Engagement metrics show (likes, comments, etc.)

- [ ] **Ask AI Button**
  - [ ] Clicking "Ask AI" on post adds content to chat
  - [ ] Chat screen shows pre-filled prompt

- [ ] **Open Source Button**
  - [ ] Clicking opens post in browser

- [ ] **Disconnect**
  - [ ] Disconnect button removes token
  - [ ] Posts from that platform disappear
  - [ ] Can re-connect after disconnect

- [ ] **Edge Cases**
  - [ ] Offline: Shows cached posts or error
  - [ ] No posts: Shows "No posts yet"
  - [ ] Rotation: Posts remain, state preserved
  - [ ] Back button: Navigation works correctly

---

## 📊 Performance & Monitoring

- [ ] Monitor API response times:
  - OAuth exchange should complete in <2s
  - Sync should complete in <5s
- [ ] Check error rates in Render logs
- [ ] Monitor token expiry handling
- [ ] Verify no memory leaks with 100+ posts

---

## 🚀 Deployment Steps

### Step 1: Deploy Backend Changes
```bash
git add backend/src/services/socialPlatforms/
git add backend/src/routes/socialRoutes.ts
git add backend/src/models/SocialToken.ts
git add backend/src/index.ts
git commit -m "feat: implement Option A social integration (Instagram, Twitter, Facebook, WhatsApp, Telegram)"
git push origin main
# Render auto-deploys
```

### Step 2: Update Render Environment
- [ ] Go to Render dashboard → Settings → Environment Variables
- [ ] Add all social platform credentials
- [ ] Review DATABASE_URL is correct
- [ ] Save changes (triggers redeploy)

### Step 3: Verify Backend Endpoints
```bash
# After Render deployment completes
curl "https://mistreal-backend.onrender.com/health"
# Should return: {"status":"ok","timestamp":"..."}

curl "https://mistreal-backend.onrender.com/api/social/auth/instagram?deviceId=test"
# Should return auth URL
```

### Step 4: Deploy Android Changes
- [ ] Update `SocialApiService` base URL
- [ ] Add AndroidManifest.xml deep link
- [ ] Add MainActivity deep link handler
- [ ] Wire up SettingsScreen connect buttons
- [ ] Wire up DashboardScreen post display
- [ ] Update dependencies (ensure Retrofit, androidx.browser, etc.)
- [ ] Build and test: `./gradlew installDebug`
- [ ] Internal testing with all 5 platforms
- [ ] Create release build when ready

### Step 5: Internal Alpha Testing
- [ ] Test each platform OAuth flow
- [ ] Verify posts appear correctly
- [ ] Check chat integration works
- [ ] Test disconnect/reconnect
- [ ] Monitor logs for errors
- [ ] Collect feedback

### Step 6: Production Release
- [ ] Tag release in git: `git tag v2.0.0-social`
- [ ] Create Google Play release
- [ ] Plan gradual rollout (10% → 50% → 100%)
- [ ] Monitor crash rates, ANRs
- [ ] Monitor API usage rates

---

## 🆘 Troubleshooting

### "Auth URL not generating"
- [ ] Check env variables are set in Render
- [ ] Verify CLIENT_ID and CLIENT_SECRET are correct
- [ ] Check redirect URI matches in platform settings

### "OAuth callback not triggering"
- [ ] Verify deep link intent filter in AndroidManifest.xml
- [ ] Test with: `adb shell am start -a android.intent.action.VIEW -d "mistreal://social-connected?platform=instagram&success=true"`
- [ ] Check logs for view event

### "Posts not appearing"
- [ ] Check SocialToken table has token entries
- [ ] Verify token hasn't expired
- [ ] Check platform API response format
- [ ] Review backend logs for API errors

### "Rate limits exceeded"
- [ ] Implement caching with Redis
- [ ] Add exponential backoff for retries
- [ ] Reduce sync frequency

### "Token expired"
- [ ] Implement token refresh logic (Instagram: 60 days, Twitter: depends on grant type)
- [ ] Catch 401/403 responses and refresh token
- [ ] Re-attempt original request after refresh

---

## 📞 Support Resources

- Instagram Graph API Docs: https://developers.facebook.com/docs/instagram-api
- Twitter API v2 Docs: https://developer.twitter.com/en/docs/twitter-api
- Facebook Graph API: https://developers.facebook.com/docs/graph-api
- WhatsApp API: https://developers.facebook.com/docs/whatsapp/cloud-api
- Telegram Bot API: https://core.telegram.org/bots/api

---

## ✨ Success Indicator

**You'll know it's working when:**
1. ✅ User taps "Connect Instagram" → OAuth browser opens
2. ✅ User authorizes → Callback received → Deep link handled
3. ✅ 10 seconds later → Instagram posts displayed in feed
4. ✅ Same for Twitter, Facebook, WhatsApp, Telegram
5. ✅ Posts show with platform icon, author, timestamp, content
6. ✅ Clicking "Ask AI" sends post to chat for analysis
7. ✅ Disconnecting removes that platform's posts
8. ✅ Zero "CONNECTION_REQUIRED" messages (Zernio GONE ✨)
9. ✅ Rotation preserves posts state
10. ✅ App crashes eliminated, performance smooth

---

**Current Status**: 🟢 **READY TO DEPLOY**

All code is written. Waiting for:
1. Platform credentials from developer accounts
2. Render environment variables configured
3. Android manifest and MainActivity updates

**Estimated time to full deployment**: 6-8 hours (including testing)

Start with **Step 1: Create Platform Developer Accounts** to begin!
