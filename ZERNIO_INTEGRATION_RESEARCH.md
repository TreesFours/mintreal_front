# Zernio Integration Research & Architecture Plan

## Current Status: ❌ Issues Found

### Problem 1: Wrong API Endpoint
- **Current**: `https://api.zerion.com/v1` (This is Zerion Finance - DeFi data, NOT social)
- **Reality**: You need `https://api.zernio.io/` or direct social platform APIs
- **Impact**: No social data is being fetched; all sync calls return CONNECTION_REQUIRED

### Problem 2: Missing OAuth Callback Handler
- `/api/social/callback` endpoint doesn't exist in backend
- Without callback, social auth flow breaks
- User tokens are never stored, so `zernioUserToken` is always null

### Problem 3: Incomplete Data Flow
```
Frontend: Settings → syncSocials() ❌ (no token)
Backend: /api/social/sync ❌ (returns CONNECTION_REQUIRED)
Display: Dashboard/Chat doesn't show posts ❌ (no data)
```

---

## Your Vision → Proper Implementation

### What You Want:
```
Posts appear in Feeds with:
├── Timestamp (when posted)
├── Source (Instagram, WhatsApp, Twitter, News)
├── Message content
├── Author/Platform info
└── Selectable in Chat screen by platform/user
```

### Real Architecture Needed:

```
┌─────────────────────────────────────────────────┐
│ Android App (Chat/Dashboard)                    │
└──────────────┬──────────────────────────────────┘
               │ 1. User clicks "Connect" on Instagram
               ▼
┌──────────────────────────────────────────────────┐
│ Settings Screen → Social Connect Dialog          │
│ Opens: OAuth URL for Instagram/WhatsApp/Twitter  │
└──────────────┬──────────────────────────────────┘
               │ 2. User authenticates on Instagram.com
               ▼
┌──────────────────────────────────────────────────┐
│ Backend: /api/social/callback                    │
│ • Receives auth code from Instagram              │
│ • Exchanges code for bearer token                │
│ • Stores token in DB (zernioUserToken)           │
└──────────────┬──────────────────────────────────┘
               │ 3. Sync button clicked
               ▼
┌──────────────────────────────────────────────────┐
│ /api/social/sync                                 │
│ • Fetches token from DB                          │
│ • Calls Instagram API with token                 │
│ • Returns posts: [Post], [Post], [Post]          │
└──────────────┬──────────────────────────────────┘
               │ 4. Posts with metadata
               ▼
┌──────────────────────────────────────────────────┐
│ Dashboard Feed                                   │
│ [📷 Instagram] Caption here (2h ago)             │
│ [💬 WhatsApp] "Hey how are you" (just now)       │
│ [📰 News] Breaking news title (1h ago)           │
└──────────────┬──────────────────────────────────┘
               │ 5. Select a post
               ▼
┌──────────────────────────────────────────────────┐
│ Chat Screen                                      │
│ Can now:                                         │
│ • Ask AI to summarize the Instagram post         │
│ • Reply to WhatsApp message via chat             │
│ • Get news context                               │
└──────────────────────────────────────────────────┘
```

---

## Three Options to Integrate Social Feeds

### Option A: ✅ RECOMMENDED - Direct Social APIs (Free Tier)

**How it works:**
- Direct OAuth with each platform
- No middleman service needed
- Full control over data

**Setup for your backend:**
```typescript
// Instagram Graph API
POST /api/social/connect (platform=instagram)
→ Redirect to: https://instagram.com/oauth/authorize?client_id=...&redirect_uri=...

// WhatsApp Business API
POST /api/social/connect (platform=whatsapp)
→ Redirect to: https://www.whatsapp.com/business/oauth?...

// Twitter/X API v2
POST /api/social/connect (platform=twitter)
→ Redirect to: https://twitter.com/i/oauth2/authorize?...

// Then /api/social/callback catches auth code and exchanges for token
```

**Pros:**
- No fees for basic tier
- Full data access
- Direct relationship with platforms

**Cons:**
- Each API has different auth flow
- More work to integrate
- Rate limits per platform
- Requires app approval from each platform

---

### Option B: 🔷 Zernio Actual Service (Premium)

**Real Zernio (NOT Zerion):**
- Actual endpoint: `https://api.zernio.io/`
- BUT: Zernio is primarily for **DeFi/crypto** socials (Discord, Telegram, Twitter)
- NOT ideal for Instagram/WhatsApp/mainstream socials

**Pros:**
- Single OAuth flow
- Handles multiple platforms
- Rate limit pooling

**Cons:**
- Focuses on crypto community platforms
- Limited Instagram/WhatsApp support
- Paid service
- Requires API key

---

### Option C: 🔶 Zapier + Webhooks (Fast but Limited)

**How it works:**
- Use Zapier to forward Instagram/WhatsApp to your backend
- Backend receives webhooks

**Pros:**
- Fastest setup (Zapier does OAuth)
- No platform approvals needed

**Cons:**
- Very limited
- Only works for basic triggers
- Paid (Zapier)
- Not real-time

---

## ✅ Recommended Implementation (Option A - Direct APIs)

### Step 1: Backend Structure
```typescript
// backend/src/services/socialService.ts - COMPLETE REWRITE

import Instagram from 'instagram-graph-api';
import TwitterApi from 'twitter-api-v2';
import whatsapp from 'whatsapp-business-api';

// 1. INSTAGRAM
export const initializeInstagramAuth = (deviceId: string) => {
  const redirectUri = `${process.env.APP_URL}/api/social/callback`;
  const authUrl = `https://api.instagram.com/oauth/authorize?client_id=${process.env.INSTAGRAM_CLIENT_ID}&redirect_uri=${redirectUri}&scope=user_profile,user_media&response_type=code`;
  return authUrl;
};

export const exchangeInstagramCode = async (code: string, deviceId: string) => {
  const response = await Instagram.exchangeCodeForToken(code);
  // Response: { access_token, user_id, ... }
  
  // Store in DB
  const user = await User.update({ deviceId }, {
    instagramAccessToken: response.access_token,
    instagramUserId: response.user_id
  });
  
  return response;
};

export const fetchInstagramPosts = async (accessToken: string) => {
  const client = new Instagram({ accessToken });
  const media = await client.getUserMedia('me');
  
  return media.map(post => ({
    id: post.id,
    platform: 'instagram',
    author: 'You', // or get logged-in user name
    content: post.caption,
    timestamp: new Date(post.timestamp),
    image: post.media_type === 'IMAGE' ? post.media_url : null,
    sourceUrl: post.permalink,
    likes: post.like_count,
    comments: post.comments_count
  }));
};

// 2. TWITTER/X
export const initializeTwitterAuth = (deviceId: string) => {
  // Similar OAuth flow
};

export const fetchTwitterPosts = async (accessToken: string) => {
  const client = new TwitterApi(accessToken);
  const tweets = await client.v2.userTimeline('me');
  
  return tweets.data?.map(tweet => ({
    id: tweet.id,
    platform: 'twitter',
    author: 'You',
    content: tweet.text,
    timestamp: new Date(tweet.created_at),
    likes: tweet.public_metrics?.like_count,
    retweets: tweet.public_metrics?.retweet_count,
    sourceUrl: `https://twitter.com/i/web/status/${tweet.id}`
  })) || [];
};

// 3. UNIFIED SYNC
export const syncAllSocials = async (user: User) => {
  const posts = [];
  
  if (user.instagramAccessToken) {
    posts.push(...await fetchInstagramPosts(user.instagramAccessToken));
  }
  if (user.twitterAccessToken) {
    posts.push(...await fetchTwitterPosts(user.twitterAccessToken));
  }
  
  // Sort by timestamp (newest first)
  posts.sort((a, b) => b.timestamp.getTime() - a.timestamp.getTime());
  
  return {
    summary: `Synced ${posts.length} posts from your socials`,
    platformUpdates: groupByPlatform(posts),
    posts: posts, // NEW: Full posts with metadata
    rawContent: posts.map(p => `[${p.platform}] ${p.author}: ${p.content}`).join('\n')
  };
};
```

### Step 2: Callback Handler
```typescript
// backend/src/index.ts

app.get('/api/social/callback', async (req, res) => {
  const { code, state, error } = req.query;
  const { deviceId, platform } = JSON.parse(Buffer.from(state as string, 'base64').toString());
  
  if (error) {
    return res.redirect(`mistreal://social-auth-error?error=${error}&platform=${platform}`);
  }
  
  try {
    // Exchange code for token based on platform
    let result;
    if (platform === 'instagram') {
      result = await exchangeInstagramCode(code as string, deviceId);
    } else if (platform === 'twitter') {
      result = await exchangeTwitterCode(code as string, deviceId);
    }
    
    // Redirect back to app with success
    res.redirect(`mistreal://social-auth-success?platform=${platform}&deviceId=${deviceId}`);
  } catch (error: any) {
    res.redirect(`mistreal://social-auth-error?error=${error.message}`);
  }
});
```

### Step 3: Updated Frontend Models
```kotlin
// Android data models

data class SocialPost(
    val id: String,
    val platform: String, // "instagram", "twitter", "whatsapp", "news"
    val author: String,
    val content: String,
    val timestamp: Date,
    val imageUrl: String? = null,
    val likes: Int? = null,
    val comments: Int? = null,
    val sourceUrl: String? = null,
    val metadata: SocialMetadata? = null
)

data class SocialMetadata(
    val platform: String,
    val platformIcon: String, // "📷", "🐦", "💬", "📰"
    val platformColor: Color,
    val sourceHandle: String? = null,
    val replyable: Boolean = false
)

// Updated response in chat
data class SocialSyncResponse(
    val summary: String,
    val platformUpdates: List<PlatformUpdate>,
    val posts: List<SocialPost>, // NEW
    val rawContent: String?
)
```

### Step 4: Dashboard Display
```kotlin
// Show posts with full metadata
LazyColumn {
    items(posts) { post in
        SocialPostCard(
            post = post,
            onSelect = {
                // Can now send to chat: "Tell me about this Instagram post"
                chatViewModel.sendMessage("Analyze: ${post.content}")
            }
        )
    }
}

@Composable
fun SocialPostCard(post: SocialPost) {
    Card {
        Row {
            Icon(text = post.metadata?.platformIcon) // "📷"
            Column {
                Text("${post.platform} • ${formatTime(post.timestamp)}")
                Text(post.author)
                Text(post.content, maxLines = 3)
                if (post.imageUrl != null) {
                    AsyncImage(post.imageUrl)
                }
            }
            Button("Ask AI") {
                // Send to chat with platform context
            }
        }
    }
}
```

### Step 5: Settings → Social Connect Flow
```kotlin
// Settings Screen

var connectingPlatform by remember { mutableStateOf<String?>(null) }

LaunchedEffect(connectingPlatform) {
    connectingPlatform?.let { platform ->
        // Get auth URL from backend
        val authUrl = viewModel.getAuthUrl(platform, deviceId)
        
        // Open browser
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(authUrl))
        context.startActivity(intent)
    }
}

// Your app needs to handle deep link: mistreal://social-auth-success
// Listen for this in MainActivity
```

---

## Data Flow Summary

### Complete Journey:
```
User → "Connect Instagram" 
  ↓
Settings → POST /api/social/connect?platform=instagram
  ↓
Backend returns: Instagram OAuth URL
  ↓
Browser opens → User logs in to Instagram
  ↓
Instagram redirects → /api/social/callback?code=...
  ↓
Backend exchanges code for token → Stores in DB
  ↓
User returns to app (deep link)
  ↓
Click "Sync" → GET /api/social/sync
  ↓
Backend fetches user's Instagram posts + other platforms
  ↓
Dashboard shows:
  [📷 Instagram] "Just had coffee!" (2 hours ago)
  [🐦 Twitter] "New feature released!" (1 hour ago)
  [💬 WhatsApp] "Meeting at 3pm?" (5 mins ago)
  [📰 News] "Market surge today" (30 mins ago)
  ↓
User selects a post → Chat
  ↓
Chat Screen: Can ask AI about it or reply
```

---

## Environment Variables Needed

```bash
# backend/.env

# Instagram
INSTAGRAM_CLIENT_ID=your_app_id
INSTAGRAM_CLIENT_SECRET=your_app_secret

# Twitter/X
TWITTER_CLIENT_ID=your_client_id
TWITTER_CLIENT_SECRET=your_client_secret

# WhatsApp Business (if different)
WHATSAPP_BUSINESS_PHONE_ID=your_phone_id
WHATSAPP_BUSINESS_TOKEN=your_access_token

# Callback URL
APP_URL=https://mistreal-backend.onrender.com
```

---

## Next Steps to Implement

1. **Choose Option**: Direct APIs (Option A) is recommended
2. **Register Apps**: 
   - Instagram: `developers.facebook.com/apps`
   - Twitter: `developer.twitter.com/en/portal/dashboard`
   - WhatsApp: `developers.facebook.com/docs/whatsapp`
3. **Backend**: Implement `/api/social/callback` and token exchange
4. **Frontend**: Add Settings screen social connect buttons
5. **Database**: Add columns for `instagramAccessToken`, `twitterAccessToken`, etc.
6. **Data Models**: Update to include full post data with metadata
7. **Dashboard**: Display posts with timestamps, platform icons, and "Ask AI" buttons
8. **Chat Integration**: Enable "Ask AI about this Instagram post" flow

---

## Why Current Zernio Isn't Working

1. ❌ URL is wrong (uses Zerion Finance instead of Zernio)
2. ❌ No OAuth callback handler
3. ❌ `zernioUserToken` is never populated
4. ❌ No actual platform authentication happening
5. ❌ Response structure doesn't match frontend expectations

## Recommendation

**Start with Twitter/X API v2** (simplest OAuth, free tier):
- Good rate limits
- Well-documented
- Then add Instagram
- Then add WhatsApp Business
- Add News aggregation (NewsAPI.org) as a final platform

Good luck! This architecture will get your social feed working end-to-end. 🎯
