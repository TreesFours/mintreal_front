# Option A: Social Media Integration Implementation Guide

## Overview
Complete multi-platform social media integration using free tier APIs from Instagram, Twitter, Facebook, WhatsApp, and Telegram. This replaces the broken Zernio integration with professional OAuth flows and unified data models.

## Backend Architecture

### 1. Platform Services (`backend/src/services/socialPlatforms/`)

#### Instagram Service
- **API**: Instagram Graph API v18.0
- **Auth Flow**: Facebook OAuth with Instagram scopes
- **Rate Limit**: 200 calls/hour (Business accounts)
- **Features**:
  - Fetch user's posts with captions
  - Get post engagement (likes, comments)
  - Support for Business/Creator accounts
- **Setup**:
  ```bash
  INSTAGRAM_CLIENT_ID=your_client_id
  INSTAGRAM_CLIENT_SECRET=your_client_secret
  INSTAGRAM_REDIRECT_URI=https://yourdomain/api/social/callback/instagram
  ```

#### Twitter Service
- **API**: Twitter API v2
- **Auth Flow**: OAuth 2.0 with PKCE
- **Rate Limit**: 300 tweets/15 min (free tier)
- **Features**:
  - Fetch user's recent tweets
  - Get tweet metrics (likes, retweets, replies)
  - Real-time timeline access
- **Setup**:
  ```bash
  TWITTER_CLIENT_ID=your_client_id
  TWITTER_CLIENT_SECRET=your_client_secret
  TWITTER_REDIRECT_URI=https://yourdomain/api/social/callback/twitter
  ```

#### Facebook Service
- **API**: Facebook Graph API v18.0
- **Auth Flow**: Facebook OAuth
- **Rate Limit**: 40k API calls/day (free tier)
- **Features**:
  - Fetch user's feed posts
  - Get post engagement
  - Support for Pages and Personal profiles
- **Setup**:
  ```bash
  FACEBOOK_APP_ID=your_app_id
  FACEBOOK_APP_SECRET=your_app_secret
  FACEBOOK_REDIRECT_URI=https://yourdomain/api/social/callback/facebook
  ```

#### WhatsApp Service
- **API**: WhatsApp Cloud API (Graph API v18.0)
- **Auth Flow**: Facebook OAuth (same as Facebook)
- **Requires**: Business phone number verification
- **Features**:
  - Send messages via WhatsApp Business API
  - Webhook support for incoming messages
  - Limited free tier capabilities
- **Setup**:
  ```bash
  FACEBOOK_APP_ID=your_app_id
  FACEBOOK_APP_SECRET=your_app_secret
  WHATSAPP_REDIRECT_URI=https://yourdomain/api/social/callback/whatsapp
  WHATSAPP_PHONE_NUMBER_ID=your_phone_number_id
  WHATSAPP_WEBHOOK_URL=https://yourdomain/api/social/whatsapp/webhook
  ```

#### Telegram Service
- **API**: Bot API
- **Auth Flow**: Manual bot token (no OAuth needed)
- **Features**:
  - Get bot updates via polling or webhook
  - Send messages through your bot
  - Support for group chats and channels
- **Setup**:
  ```bash
  TELEGRAM_BOT_TOKEN=your_bot_token
  TELEGRAM_WEBHOOK_URL=https://yourdomain/api/social/telegram/webhook
  ```

### 2. Routes (`backend/src/routes/socialRoutes.ts`)

#### OAuth Initiation
```
GET /api/social/auth/instagram?deviceId=<device>
GET /api/social/auth/twitter?deviceId=<device>
GET /api/social/auth/facebook?deviceId=<device>
GET /api/social/auth/whatsapp?deviceId=<device>
```

**Response**:
```json
{
  "authUrl": "https://api.instagram.com/oauth/authorize?...",
  "deviceId": "android-device-123"
}
```

#### OAuth Callback
```
GET /api/social/callback/instagram?code=<auth_code>&state=<deviceId>
GET /api/social/callback/twitter?code=<auth_code>&state=<deviceId>
GET /api/social/callback/facebook?code=<auth_code>&state=<deviceId>
GET /api/social/callback/whatsapp?code=<auth_code>&state=<deviceId>
```

**Response**: Redirects to `mistreal://social-connected?platform=<name>&success=true/false`

#### Sync All Platforms
```
POST /api/social/sync
```

**Request**:
```json
{
  "deviceId": "android-device-123"
}
```

**Response**:
```json
{
  "summary": "Synced 47 posts from all platforms",
  "posts": [
    {
      "id": "insta-12345",
      "platform": "instagram",
      "author": "john_doe",
      "content": "Beautiful sunset at the beach! 🌅",
      "timestamp": "2024-01-15T18:30:00Z",
      "imageUrl": "https://...",
      "likes": 234,
      "comments": 15,
      "sourceUrl": "https://instagram.com/...",
      "platformIcon": "📷",
      "platformColor": "#E4405F"
    },
    // ... more posts
  ],
  "platformUpdates": [
    {
      "platform": "instagram",
      "count": 12,
      "platformIcon": "📷",
      "platformColor": "#E4405F",
      "recentMessage": "Beautiful sunset at the beach! 🌅"
    },
    // ... other platforms
  ],
  "platformStatus": {
    "instagram": "✅ 12 posts",
    "twitter": "✅ 8 tweets",
    "facebook": "✅ 15 posts",
    "whatsapp": "⚪ Not connected",
    "telegram": "⚪ Not connected"
  }
}
```

#### Disconnect Platform
```
POST /api/social/disconnect/{platform}
```

**Request**:
```json
{
  "deviceId": "android-device-123"
}
```

**Response**:
```json
{
  "success": true,
  "platform": "instagram",
  "message": "instagram disconnected"
}
```

### 3. Database Model

**SocialToken Table** (Sequelize):
```sql
CREATE TABLE "SocialTokens" (
  id UUID PRIMARY KEY,
  deviceId VARCHAR(255) NOT NULL,
  platform ENUM('instagram', 'twitter', 'facebook', 'whatsapp', 'telegram'),
  accessToken TEXT NOT NULL,
  refreshToken TEXT,
  platformUserId VARCHAR(255),
  expiresAt TIMESTAMP,
  metadata JSON,
  connectedAt TIMESTAMP DEFAULT NOW(),
  UNIQUE(deviceId, platform)
);
```

## Android Integration

### 1. Data Models (`app/src/main/java/.../data/models/SocialPost.kt`)

```kotlin
@Serializable
data class SocialPost(
    val id: String,
    val platform: String,
    val author: String,
    val content: String,
    val timestamp: String,
    val imageUrl: String? = null,
    val likes: Int? = null,
    val comments: Int? = null,
    val retweets: Int? = null,
    val sourceUrl: String? = null,
    val platformIcon: String,
    val platformColor: String
)
```

### 2. API Service (`app/src/main/java/.../data/api/SocialApiService.kt`)

```kotlin
interface SocialApiService {
    @GET("auth/instagram")
    suspend fun getInstagramAuthUrl(@Query("deviceId") deviceId: String): SocialAuth
    
    @POST("sync")
    suspend fun syncAllPlatforms(@Body request: Map<String, String>): SocialSyncResponse
    
    @POST("disconnect/{platform}")
    suspend fun disconnectPlatform(
        @Path("platform") platform: String,
        @Body request: Map<String, String>
    ): Map<String, Any>
}
```

### 3. Repository (`app/src/main/java/.../data/repository/SocialRepository.kt`)

Handles OAuth flows, API calls, and platform communication.

### 4. Use Cases

- `GetSocialPostsUseCase`: Fetch and merge posts from all platforms
- `ConnectSocialPlatformUseCase`: Initiate OAuth for each platform
- `DisconnectSocialPlatformUseCase`: Revoke tokens and cleanup

### 5. UI Components

#### SocialPostCard
Displays individual posts with:
- Platform icon + author name
- Post content with text wrapping
- Image/media if available
- Engagement metrics (likes, comments, retweets)
- "Ask AI" button to analyze post
- "Open" button to view source
- Timestamp in relative format (e.g., "2 hours ago")

### 6. Deep Link Handling (AndroidManifest.xml)

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

In `MainActivity.kt`:
```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
    // Handle OAuth callbacks
    val uri = intent.data
    if (uri?.scheme == "mistreal" && uri.host == "social-connected") {
        val platform = uri.getQueryParameter("platform")
        val success = uri.getQueryParameter("success").toBoolean()
        
        if (success) {
            showSnackbar("✅ $platform connected!")
            // Refresh social feed
            viewModel.syncSocialPosts()
        } else {
            showSnackbar("❌ Connection failed")
        }
    }
}
```

## Integration Sequence

### Step 1: User Initiates Connection
```
User taps "Connect Instagram" in Settings
→ SettingsViewModel.connectPlatform("instagram")
→ SocialRepository.initiateInstagramConnect(deviceId)
→ Opens CustomTabs with auth URL
```

### Step 2: OAuth Authorization
```
User authorizes on platform website
→ Platform redirects to /api/social/callback/instagram?code=xxx&state=deviceId
→ Backend exchanges code for token
→ Stores token in SocialToken table
→ Redirects to mistreal://social-connected?success=true
```

### Step 3: App Receives Callback
```
Deep link triggers MainActivity.onCreate()
→ Recognized as social-connected intent
→ Triggers sync via GetSocialPostsUseCase
→ Downloads and displays posts
```

### Step 4: Sync Loop
```
DashboardScreen displays SocialPosts
→ User can tap "Ask AI" on any post
→ Sends post content to ChatViewModel
→ AI analyzes/summarizes/responds to post
```

## Environment Variables Required

```bash
# Instagram
INSTAGRAM_CLIENT_ID=
INSTAGRAM_CLIENT_SECRET=
INSTAGRAM_REDIRECT_URI=https://yourdomain/api/social/callback/instagram

# Twitter
TWITTER_CLIENT_ID=
TWITTER_CLIENT_SECRET=
TWITTER_REDIRECT_URI=https://yourdomain/api/social/callback/twitter

# Facebook
FACEBOOK_APP_ID=
FACEBOOK_APP_SECRET=
FACEBOOK_REDIRECT_URI=https://yourdomain/api/social/callback/facebook

# WhatsApp
WHATSAPP_REDIRECT_URI=https://yourdomain/api/social/callback/whatsapp
WHATSAPP_PHONE_NUMBER_ID=
WHATSAPP_WEBHOOK_URL=

# Telegram
TELEGRAM_BOT_TOKEN=
TELEGRAM_WEBHOOK_URL=https://yourdomain/api/social/telegram/webhook

# App Config
REDIRECT_DOMAIN=yourdomain
```

## Testing Checklist

- [ ] Instagram OAuth flow completes and fetches posts
- [ ] Twitter OAuth flow completes and fetches tweets
- [ ] Facebook OAuth flow completes and fetches feed
- [ ] WhatsApp token exchange works (manual webhook testing)
- [ ] Telegram polling/webhook receives messages
- [ ] Posts display in correct order (newest first)
- [ ] Clicking "Ask AI" sends post to chat
- [ ] Disconnecting revokes token and hides posts
- [ ] Rotation doesn't lose social feed
- [ ] Offline mode gracefully handles missing posts

## Next Steps

1. **Get Platform Credentials**:
   - Go to each platform's developer console
   - Create OAuth app
   - Set redirect URIs to your backend domain

2. **Deploy Backend**:
   - Install dependencies: `npm install axios`
   - Set all env variables on Render
   - Test routes with Postman

3. **Test Android Integration**:
   - Update `SocialApiService` endpoints
   - Test OAuth flows in debug mode
   - Verify deep link handling with `adb shell`

4. **Deploy to Production**:
   - Switch to production credentials
   - Enable HTTPS for all endpoints
   - Monitor OAuth error rates

## Troubleshooting

**"Platform not connected" message**:
- Check if token is stored in SocialToken table
- Verify `syncAllPlatforms()` can access tokens
- Ensure token hasn't expired

**Posts not appearing**:
- Check platform-specific API rate limits
- Verify OAuth scopes include read permissions
- Check API response format matches `SocialPost` model

**Deep link not triggering**:
- Verify AndroidManifest.xml has correct intent filter
- Check `adb shell dumpsys package <app-package>` for intent filter registration
- Test with: `adb shell am start -a android.intent.action.VIEW -d "mistreal://social-connected?platform=instagram&success=true"`

---

**Status**: ✅ Ready for implementation
**Priority**: HIGH (Replaces broken Zernio)
**Effort**: 8-12 hours (backend + Android integration + testing)
