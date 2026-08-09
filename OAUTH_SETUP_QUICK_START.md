# Quick Start - Getting OAuth Credentials

## Step-by-Step Platform Setup

### 1️⃣ Twitter/X (5-10 minutes)

**Get Credentials**:
1. Go to [developer.twitter.com/en/portal/dashboard](https://developer.twitter.com/en/portal/dashboard)
2. Click "Create an app" (or select existing app)
3. In sidebar, click "Keys and tokens"
4. Scroll to "OAuth 2.0 User Context Settings"
5. Click "Set up" (if not already set up)
6. Copy **Client ID** and **Client Secret**

**Configure Callback URL**:
1. In sidebar, go to "Settings" → "Authentication Settings"
2. Enable **OAuth 2.0**
3. Select **Authorization Code** with **PKCE**
4. Callback URLs: Add `https://mistreal-backend.onrender.com/api/social/callback`
5. Website URL: `https://mistreal-backend.onrender.com`

**Update `.env`**:
```bash
TWITTER_CLIENT_ID=your_copied_client_id
TWITTER_CLIENT_SECRET=your_copied_client_secret
```

**Test Connection**:
Visit: `https://mistreal-backend.onrender.com/api/social/connect/twitter?deviceId=test123`
Should redirect to Twitter login page.

---

### 2️⃣ Instagram (10-15 minutes)

**Get Credentials**:
1. Go to [developers.facebook.com](https://developers.facebook.com/)
2. Click "My Apps" → "Create App"
3. Choose **Business** type
4. Fill in app name, contact email, etc.
5. In app dashboard, find "App ID" and "App Secret"
6. Copy both values

**Configure Instagram Product**:
1. In app dashboard, click "Add Product"
2. Find **Instagram Graph API** → Click "Set Up"
3. In left sidebar, go to **Instagram Graph API** → **Settings**
4. Copy **Access Token** (or Generate new one)

**Configure Callback URL**:
1. Go to **Settings** → **Basic**
2. Add App Domains: `mistreal-backend.onrender.com`
3. Add Redirect URIs: `https://mistreal-backend.onrender.com/api/social/callback`

**Update `.env`**:
```bash
INSTAGRAM_CLIENT_ID=your_app_id
INSTAGRAM_CLIENT_SECRET=your_app_secret
```

---

### 3️⃣ WhatsApp (Uses Same Facebook App)

**No additional setup needed!** Use the same app ID/secret as Instagram above.

**Update `.env`**:
```bash
WHATSAPP_CLIENT_ID=your_app_id  # Same as INSTAGRAM_CLIENT_ID
WHATSAPP_CLIENT_SECRET=your_app_secret  # Same as INSTAGRAM_CLIENT_SECRET
```

---

### 4️⃣ Facebook (Uses Same Facebook App)

**No additional setup needed!** Use the same app ID/secret.

**Update `.env`**:
```bash
FACEBOOK_CLIENT_ID=your_app_id  # Same as INSTAGRAM_CLIENT_ID
FACEBOOK_CLIENT_SECRET=your_app_secret  # Same as INSTAGRAM_CLIENT_SECRET
```

---

### 5️⃣ LinkedIn (10-15 minutes)

**Get Credentials**:
1. Go to [linkedin.com/developers/apps](https://linkedin.com/developers/apps)
2. Click "Create app"
3. Fill in: App name, LinkedIn Page, App logo
4. Accept terms, "Create app"
5. Go to **Auth** tab
6. Copy **Client ID** and **Client Secret**

**Configure Redirect URL**:
1. Under **Redirect URLs**, add: `https://mistreal-backend.onrender.com/api/social/callback`
2. Click "Update"

**Update `.env`**:
```bash
LINKEDIN_CLIENT_ID=your_client_id
LINKEDIN_CLIENT_SECRET=your_client_secret
```

---

## 📋 Checklist

- [ ] Twitter Client ID & Secret → `.env`
- [ ] Instagram App ID & Secret → `.env`
- [ ] WhatsApp using Facebook App
- [ ] Facebook using Facebook App
- [ ] LinkedIn Client ID & Secret → `.env`
- [ ] Set `APP_URL=https://mistreal-backend.onrender.com`
- [ ] All callback URLs registered on each platform
- [ ] Run: `git add . && git commit -m "Add OAuth credentials"` (in production, use secrets manager!)
- [ ] Deploy backend
- [ ] Test: Open Settings → "Manage Social Connections" → Click a platform
- [ ] Verify redirect to login page

---

## ⚠️ Important Security Notes

**DO NOT commit credentials to Git!**

For production, use:
- **Render**: Environment variables in dashboard
- **Heroku**: Config vars
- **AWS**: Secrets Manager
- **Docker**: Secrets file (not in image)
- **Kubernetes**: Secrets

Example `.env` for local development:
```bash
# Local only - NEVER commit this to git
TWITTER_CLIENT_ID=xxx
TWITTER_CLIENT_SECRET=xxx
# ... etc
```

Then add `.env` to `.gitignore`:
```
.env
.env.local
```

---

## 🧪 Testing Each Connection

### Manual Test in Backend Terminal

```bash
# Test Twitter OAuth URL generation
curl "https://mistreal-backend.onrender.com/api/social/connect/twitter?deviceId=test123"
# Should return: { "authUrl": "https://twitter.com/i/oauth2/authorize?..." }

# Test Instagram
curl "https://mistreal-backend.onrender.com/api/social/connect/instagram?deviceId=test123"
# Should return: { "authUrl": "https://api.instagram.com/oauth/authorize?..." }
```

### App-Level Test

1. Open app Settings
2. Click "Manage Social Connections"
3. Should see platform cards loading
4. Click Instagram (or any platform)
5. Should open browser → platform login
6. After login, should redirect back to app
7. Check for "Connected successfully" message

---

## 🔧 Troubleshooting

### "Client ID not configured"
- [ ] Check `.env` file has the variable
- [ ] Restart backend service after updating `.env`
- [ ] Verify variable name is correct (check typos)

### "Invalid client ID"
- [ ] Re-verify you copied the ID correctly from platform dashboard
- [ ] Make sure it's **Client ID**, not App ID
- [ ] Some platforms have multiple ID types

### "Redirect URI mismatch"
- [ ] Check callback URL registered on platform dashboard
- [ ] Must be: `https://mistreal-backend.onrender.com/api/social/callback`
- [ ] Must use HTTPS (not http://)
- [ ] Some platforms are case-sensitive

### "Failed to exchange code for token"
- [ ] Check client secret is correct
- [ ] Verify redirect URI matches
- [ ] Check platform API status page
- [ ] Look at backend logs: `docker logs mistreal-backend`

---

## Next Phases (Optional)

After basic OAuth works:

1. **Token Refresh** (~2-3 hours)
   - Instagram, Facebook tokens expire
   - Implement refresh token flow

2. **Posting** (~4-6 hours)
   - Create POST endpoint for sharing to socials
   - Handle platform-specific formats

3. **Real-time Sync** (~6-8 hours)
   - Webhooks instead of polling
   - Push notifications on new messages

4. **Error Recovery** (~2-3 hours)
   - Auto-retry failed syncs
   - Queue and retry logic
