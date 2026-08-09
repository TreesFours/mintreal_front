# Debugging Guide - Social Connections

## Problem 1: "ZERNIO_API_KEY is missing"

### ❌ You're Still Seeing This Error?

This means the old code is still running. The app is calling OLD functions.

**Solution:**
```bash
# Make sure you've updated the backend files:
✓ backend/src/services/socialService.ts
✓ backend/src/routes/socialRoutes.ts
✓ backend/src/services/socialPlatforms/unified.ts

# Restart backend after changes:
npm run dev

# Or if deployed:
git push  # and trigger redeploy on Render
```

---

## Problem 2: "Platform X not supported"

### ✓ Checking Supported Platforms

When connecting a platform, check backend logs:

```bash
curl https://mistreal-backend.onrender.com/api/social/connect/instagram?deviceId=test123
```

**If you see**:
```json
{ "error": "Platform instagram not supported" }
```

**Check**:
1. Platform name is lowercase
2. Platform is in the OAUTH_HANDLERS registry in socialService.ts

```typescript
const OAUTH_HANDLERS: Record<string, any> = {
    'twitter': TwitterOAuth,  // ✓ supported
    'x': TwitterOAuth,        // ✓ supported
    'instagram': InstagramOAuth,  // ✓ supported
    'whatsapp': WhatsAppOAuth,    // ✓ supported
    // etc.
};
```

---

## Problem 3: "Platform Client ID not configured"

### ❌ Error: Can't create auth session

```
Error: TWITTER_CLIENT_ID not configured
```

**Causes:**
1. Environment variable not set
2. Variable name misspelled
3. Backend restarted without reading `.env`

**Debug Steps:**

```bash
# 1. Check .env file exists
cat backend/.env | grep TWITTER

# Should output:
# TWITTER_CLIENT_ID=your_id_here
# TWITTER_CLIENT_SECRET=your_secret_here

# 2. Verify backend is reading it
# Check backend logs for your variable
docker logs mistreal-backend | grep -i "twitter\|client"

# 3. Test directly
curl -H "Authorization: Bearer $TWITTER_CLIENT_SECRET" \
  https://mistreal-backend.onrender.com/api/test-env

# 4. Manual test in backend
npm run dev
# Watch console for startup logs that show env vars
```

**Solution:**
1. Add credentials to `.env`
2. Restart `npm run dev`
3. In production, update config vars on Render/Heroku

---

## Problem 4: "Failed to exchange code for token"

### ❌ OAuth code → token exchange failed

```
Backend logs show:
Error: Failed to exchange Twitter code for token
Response: { error: 'invalid_client', error_description: '...' }
```

**Checking Causes:**

```typescript
// In twitterAuth.ts, the error comes from:
const response = await axios.post(`${TWITTER_API_BASE}/oauth/token`, {
    client_id: clientId,
    client_secret: clientSecret,  // ❌ If wrong, platform rejects
    code: code,                    // ❌ If expired (>10min), invalid
    redirect_uri: callbackUrl      // ❌ If mismatched, rejected
});
```

**Debug Protocol:**

```bash
# 1. Get the exact error from backend logs
docker logs mistreal-backend | tail -20
# Look for: "Twitter token exchange failed: ..."

# 2. Check callback URL matches
# Platform setting should have:
#   https://mistreal-backend.onrender.com/api/social/callback
# NOT:
#   https://mistreal-backend.onrender.com/api/social/callback/twitter  (❌ wrong)
#   https://yourdomain.com/...  (❌ wrong domain)
#   http://... (❌ must be HTTPS)

# 3. Verify credentials are correct
# Go to https://developer.twitter.com/en/portal/aut/oauth2
# Compare: Your app's "Client ID" with your .env TWITTER_CLIENT_ID

# 4. Check auth code isn't expired
# Auth codes are only valid for ~10 minutes
# If user takes too long, they'll get error
```

**Solutions:**

| Error | Solution |
|-------|----------|
| `invalid_client` | Client ID or Secret wrong |
| `invalid_redirect_uri` | Redirect URL mismatch |
| `invalid_grant` | Code expired (>10 min old) |
| `access_denied` | User didn't grant permission |
| `invalid_scope` | Requested scope not allowed |

---

## Problem 5: "Callback never received"

### ❌ User redirects to Instagram, never comes back

**Possible Causes:**

1. **Callback URL never registered** on platform
   ```
   Instagram expects: https://mistreal-backend.onrender.com/api/social/callback
   But your app has: https://example.com/api/social/callback
   → Instagram redirects to non-existent URL
   ```

2. **Deep link not configured** in Android
   ```
   App won't handle: mistreal://social-connected?...
   → User stuck on browser
   ```

3. **Redirect is happening but not parsing**
   ```
   Backend receives callback but fails to parse state
   → No success message
   ```

**Debug Protocol:**

```bash
# 1. Check if backend callback route exists
curl -v https://mistreal-backend.onrender.com/api/social/callback \
  -G -d "code=test&state=test"
# Should return 3xx redirect or JSON response (not 404)

# 2. Check callback is registered on platform
# Twitter: https://developer.twitter.com → App Settings → Auth Settings
# Should show: https://mistreal-backend.onrender.com/api/social/callback

# 3. Manually test the flow
# Step 1: Get auth URL
AUTH_URL=$(curl -s "https://mistreal-backend.onrender.com/api/social/connect/twitter?deviceId=test123" | jq -r '.authUrl')

# Step 2: Open in browser
echo "Open: $AUTH_URL"

# Step 3: Check logs when you come back
docker logs mistreal-backend | grep -i "callback"
# Should see: "Token exchange successful" or error message

# 4. Check deep link config in Android
# File: AndroidManifest.xml should have:
# <intent-filter>
#     <action android:name="android.intent.action.VIEW" />
#     <category android:name="android.intent.category.BROWSABLE" />
#     <category android:name="android.intent.category.DEFAULT" />
#     <data android:scheme="mistreal" android:host="social-connected" />
# </intent-filter>
```

---

## Problem 6: Token Stored But Posts Don't Sync

### ❌ Connected platform shows no posts

```bash
# 1. Verify token was stored
SELECT deviceId, twitterAccessToken, connectedPlatforms 
FROM Users 
WHERE deviceId = 'your-device-id';

# If twitterAccessToken is NULL:
#   → Token wasn't saved during callback
#   → Check callback route, check DB save logic

# If twitterAccessToken exists:
#   2. Check if it's valid by calling platform API
curl -H "Authorization: Bearer $TOKEN" \
  https://api.twitter.com/2/users/me
# If error: token is invalid/expired

#   3. Try manual sync call
curl -X POST https://mistreal-backend.onrender.com/api/social/sync \
  -H "Content-Type: application/json" \
  -d '{"deviceId": "your-device-id"}'

# Should return:
{
  "summary": "Synced 15 posts from 2 platforms",
  "posts": [ ... ],
  "platformUpdates": [ ... ]
}
```

**Debugging `/api/social/sync`**:

```bash
# Check backend logs for errors:
docker logs mistreal-backend | grep -i "sync\|instagram\|twitter"

# Common errors:
# "Rate limit exceeded" → Wait, then retry
# "Token expired" → Need to implement refresh logic
# "403 Forbidden" → Check OAuth scopes
# "posts is empty" → User has no posts, or API returns empty
```

---

## Problem 7: Platform Specific Issues

### Twitter/X Issues

**Error: "OAuth 2.0 not enabled"**
```
Solution: Go https://developer.twitter.com/en/portal/
  → Your app settings
  → Authentication settings
  → Enable "OAuth 2.0"
```

**Error: "Invalid scopes"**
```
Check scopes in twitterAuth.ts:
scope: 'tweet.read tweet.write users.read follows.read follows.write'

Must be enabled on Twitter:
  https://developer.twitter.com/ → Your app → Permissions
```

---

### Instagram Issues

**Error: "App not yet approved for live mode"**
```
Instagram apps start in "Development" mode
You can only access your own account

Solution: 
  1. Add your test Instagram accounts to app
  2. Get their permission
  3. They must be added as "Test Users" in app
```

**Error: "Token expired"**
```
Instagram user tokens last ~60 days
Need to: 
  1. Refresh token before it expires
  2. Or ask user to reconnect

Add to User model:
  instagramTokenExpiresAt: Date
  
Before using token, check:
  if (Date.now() > user.instagramTokenExpiresAt - 7days)
    refreshToken()
```

---

### WhatsApp Issues

**Error: "You must use a WhatsApp Business Account"**
```
WhatsApp Business API needs:
  1. Business account (not personal)
  2. Phone number documented on account
  3. Meta Business Manager setup

Solution:
  Go to: https://www.facebook.com/business/
  Register for WhatsApp Business
```

---

## Problem 8: Database Migration Issues

### ❌ Error: "Column twitterAccessToken does not exist"

```
Sequelize tried to sync but failed
```

**Solution:**

```bash
# Method 1: Manual migration (if using migrations)
npx sequelize-cli migration:generate --name AddSocialTokens
# Edit the migration to add columns
npx sequelize-cli db:migrate

# Method 2: Force sync (development only!)
# In your index.ts:
const sequelize = new Sequelize(...);
await sequelize.sync({ alter: true, force: false });
// force: false = don't drop tables, just alter them

# Method 3: Manual SQL
ALTER TABLE Users 
ADD COLUMN twitterAccessToken TEXT;
ADD COLUMN instagramAccessToken TEXT;
-- etc.
```

---

## Problem 9: CORS Issues

### ❌ Error: "No 'Access-Control-Allow-Origin' header"

**This happens when**: Frontend and backend are on different domains and CORS isn't configured

**Solution:**

```typescript
// In backend/src/index.ts
import cors from 'cors';

app.use(cors({
    origin: ['https://mistreal-backend.onrender.com', 'http://localhost:3000'],
    credentials: true
}));
```

---

## Monitoring & Health Checks

### Regular Health Check Script

```bash
#!/bin/bash
# health-check.sh - Run this to verify setup

echo "🔍 Checking Social Connections Setup..."

# 1. Check env vars
if [ -z "$TWITTER_CLIENT_ID" ]; then
    echo "❌ TWITTER_CLIENT_ID not set"
else
    echo "✓ TWITTER_CLIENT_ID set"
fi

# 2. Check database
curl -s https://mistreal-backend.onrender.com/api/social/platforms?deviceId=test | jq .
# Should return: [{ id, name, icon, isProOnly }, ...]

# 3. Check OAuth handler loads
curl -s https://mistreal-backend.onrender.com/api/social/connect/twitter?deviceId=test
# Should have "authUrl" field

# 4. Check database connection
curl -s https://mistreal-backend.onrender.com/health
# Should return: { status: "ok" }

echo "✅ Setup verification complete"
```

---

## Logging Setup

### Add Detailed Logging for Debugging

```typescript
// In socialService.ts
export const createConnectSession = async (platform: string, deviceId: string) => {
    console.log(`[OAuth] Starting ${platform} connection for device: ${deviceId}`);
    const appUrl = process.env.APP_URL;
    console.log(`[OAuth] App URL: ${appUrl}`);
    
    try {
        const handler = OAUTH_HANDLERS[platform.toLowerCase()];
        if (!handler) {
            throw new Error(`Platform ${platform} not supported`);
        }
        
        const authUrl = handler.getAuthUrl(deviceId, callbackUrl);
        console.log(`[OAuth] Generated auth URL: ${authUrl.split('?')[0]}...`);
        return authUrl;
    } catch (error: any) {
        console.error(`[OAuth] ERROR creating session:`, error.message);
        throw error;
    }
};
```

### View Logs

```bash
# Local development
npm run dev
# Logs print to console

# Production (Render)
docker logs mistreal-backend --follow
# Shows logs in real-time

# Production (Heroku)
heroku logs --tail
```

---

## Quick Reference Checklist

- [ ] All platform credentials added to `.env`
- [ ] Backend restarted after `.env` changes
- [ ] Callback URL registered on each platform
- [ ] Deep link configured in AndroidManifest.xml
- [ ] Database migrations run (new token columns)
- [ ] CORS enabled for your domain
- [ ] Test OAuth flow: Settings → Connect → Platform → Grant permission
- [ ] Check database: Token should exist in Users table
- [ ] Call /api/social/sync: Should return posts
- [ ] Posts display in Feeds tab

See `SOCIAL_CONNECTIONS_GUIDE.md` for setup reference.
