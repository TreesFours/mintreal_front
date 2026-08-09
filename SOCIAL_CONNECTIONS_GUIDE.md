# Social Connections - Implementation Guide

## ✅ What's Been Implemented

### 1. **Direct OAuth Flow** (Replaces Broken Zernio API)
The system now uses **direct platform OAuth** instead of the Zernio unified API that was failing.

**Supported Platforms:**
- **Free Tier (2 platforms)**: Twitter/X, WhatsApp
- **Premium Tier**: All platforms (+ Instagram, Facebook, LinkedIn, etc.)

### 2. **Backend Platform Handlers**

Created platform-specific OAuth handlers:
- `twitterAuth.ts` - X/Twitter OAuth 2.0 + posting
- `instagramAuth.ts` - Instagram Graph API
- `whatsappAuth.ts` - WhatsApp Business API
- `socialAuthHandlers.ts` - Facebook & LinkedIn

Each handler:
- Generates OAuth authorization URL
- Exchanges auth code for access token
- Fetches user posts/messages
- Returns structured data in unified format

### 3. **Updated Routes**

**`/api/social/connect/:platform?deviceId=xxx`**
- Generates OAuth link for each platform
- User clicks → redirects to platform's login
- Platform user logs in & grants permission

**`/api/social/callback`**
- Receives auth code from platform
- Exchanges for access token
- Stores token in User model
- Redirects back to app: `mistreal://social-connected?platform=X&success=true`

**`/api/social/sync` (POST)**
- Fetches posts from all connected platforms
- Returns: `{ summary, posts[], platformUpdates[] }`

**`/api/social/disconnect/:platform` (POST)**
- Revokes platform access
- Clears stored token

### 4. **Updated User Model**

New fields added to store platform tokens:
```typescript
twitterAccessToken: string | null
twitterRefreshToken: string | null
instagramAccessToken: string | null
whatsappAccessToken: string | null
facebookAccessToken: string | null
linkedinAccessToken: string | null
```

---

## 🔧 Setup Instructions

### Step 1: Obtain OAuth Credentials

#### **Twitter/X**
1. Go to [developer.twitter.com](https://developer.twitter.com/en/portal/dashboard)
2. Create an app (if you don't have one)
3. Go to **Settings → Authentication Settings**
4. Enable **OAuth 2.0** with **Authorization Code**
5. Add callback URL: `https://yourdomain.com/api/social/callback`
6. Copy: `TWITTER_CLIENT_ID`, `TWITTER_CLIENT_SECRET`

#### **Instagram**
1. Go to [developers.facebook.com](https://developers.facebook.com/)
2. Create an app (if you don't have one)
3. Add **Instagram Graph API** product
4. Go to **Settings → Basic** for app ID/secret
5. Copy: `INSTAGRAM_CLIENT_ID`, `INSTAGRAM_CLIENT_SECRET`

#### **WhatsApp**
1. Same as Instagram - uses Facebook apps
2. Add **WhatsApp Business** product to same app
3. Copy: `WHATSAPP_CLIENT_ID`, `WHATSAPP_CLIENT_SECRET`

#### **Facebook**
1. Same Facebook app
2. Add **Facebook Graph API** product
3. Copy: `FACEBOOK_CLIENT_ID`, `FACEBOOK_CLIENT_SECRET`

#### **LinkedIn**
1. Go to [linkedin.com/developers](https://www.linkedin.com/developers)
2. Create an app
3. Copy: `LINKEDIN_CLIENT_ID`, `LINKEDIN_CLIENT_SECRET`

### Step 2: Set Environment Variables

Update your `.env` file with the credentials:

```env
# Twitter/X
TWITTER_CLIENT_ID=your_id_here
TWITTER_CLIENT_SECRET=your_secret_here

# Instagram
INSTAGRAM_CLIENT_ID=your_id_here
INSTAGRAM_CLIENT_SECRET=your_secret_here

# WhatsApp
WHATSAPP_CLIENT_ID=your_id_here
WHATSAPP_CLIENT_SECRET=your_secret_here

# Facebook
FACEBOOK_CLIENT_ID=your_id_here
FACEBOOK_CLIENT_SECRET=your_secret_here

# LinkedIn
LINKEDIN_CLIENT_ID=your_id_here
LINKEDIN_CLIENT_SECRET=your_secret_here

# App URL (for callbacks)
APP_URL=https://yourdomain.com
```

### Step 3: Deploy Database Changes

The new User fields (`twitterAccessToken`, `instagramAccessToken`, etc.) need to be added:

```bash
# If using Sequelize migrations
npm run migrate

# Or let it auto-sync (dev only)
# Database will sync on app startup
```

---

## 📱 User Flow

### Connecting a Social Account

1. **User**: Opens Settings → "Manage Social Connections"
2. **Frontend**: Loads platform cards (Twitter, WhatsApp for free; all for premium)
3. **User**: Clicks "Instagram" card
4. **Frontend**: 
   - Calls `SettingsViewModel.connectSocial("instagram")`
   - Opens browser to `https://backend.com/api/social/connect/instagram?deviceId=xxx`
5. **Backend**: Generates OAuth URL, redirects to Instagram.com
6. **Instagram**: User logs in, grants permission
7. **Instagram**: Redirects to `https://backend.com/api/social/callback?code=xxx&state=yyy`
8. **Backend**:
   - Decodes state (contains deviceId + platform)
   - Exchanges code for access token via Instagram API
   - Stores token in DB
   - Redirects to: `mistreal://social-connected?platform=instagram&success=true&deviceId=xxx`
9. **Android App**: Handles deep link, shows success notification
10. **Posts**: Now sync when user clicks sync or on schedule

---

## 📊 Data Returned from `/api/social/sync`

```json
{
  "summary": "Synced 5 posts from 2 platforms",
  "platformUpdates": [
    {
      "platform": "instagram",
      "count": 3,
      "platformIcon": "📷",
      "platformColor": "#E4405F",
      "platformDisplayName": "Instagram",
      "connected": true
    }
  ],
  "posts": [
    {
      "id": "12345",
      "platform": "instagram",
      "author": "your_username",
      "content": "Amazing sunset! 🌅",
      "timestamp": "2024-08-02T10:30:00Z",
      "imageUrl": "https://...",
      "likes": 42,
      "comments": 5,
      "sourceUrl": "https://instagram.com/...",
      "platformIcon": "📷",
      "platformColor": "#E4405F",
      "platformDisplayName": "Instagram"
    }
  ],
  "rawContent": "[instagram] your_username: Amazing sunset! 🌅\n..."
}
```

---

## 🛠️ Troubleshooting

### Error: "TWITTER_CLIENT_ID not configured"
**Solution**: Ensure environment variables are set on the server. Use `.env` file or platform-specific secrets manager.

### Error: "Failed to exchange code for token"
**Causes**:
1. Callback URL mismatch - make sure it matches OAuth app settings
2. Invalid/expired credentials
3. Platform API rate limit exceeded

**Solution**: 
- Check error logs: `console.error` output in backend
- Verify callback URL in platform settings
- Refresh credentials

### Posts not showing in Feed
**Causes**:
1. User not connected to platform
2. Platform token expired (Instagram, Facebook)
3. Free user trying to sync premium platform

**Solution**:
- Call `GET /api/social/platforms?deviceId=xxx` to check available platforms
- Check `user.connectedPlatforms` array
- Trigger reconnect if token expired

---

## 🔐 Security Notes

1. **Never expose access tokens** in frontend code
2. Use HTTPS for all OAuth redirects
3. Store tokens in secure database fields (encrypted if possible)
4. Implement token refresh for long-lived access (needed for Instagram, Facebook)
5. Add rate limiting to prevent abuse

---

## 📈 Next Steps

### 1. **Refresh Token Handling**
Some platforms (Instagram, Facebook) tokens expire. Implement refresh logic:

```typescript
if (tokenExpired) {
  newToken = await platform.refreshToken(refreshToken);
}
```

### 2. **Posting to Socials**
Implement actions to POST:
- Tweet on Twitter
- Share story on Instagram
- Send WhatsApp message
- Post on Facebook

### 3. **Real-time Webhooks**
Instead of polling, set up webhooks:
- Instagram: Real-time feed updates
- WhatsApp: Message webhooks
- Twitter: Stream API subscriptions

### 4. **Error Recovery**
- Automatically retry failed syncs
- Queue failed posts for retry
- Notify user of connection issues

---

## 📞 Support

For issues:
1. Check backend logs: `docker logs mistreal-backend`
2. Verify `.env` variables are set
3. Test OAuth flow manually via platform's sandbox
4. Check platform API documentation for changes
