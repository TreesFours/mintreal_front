# User Experience - What You'll See

## Settings Flow with Fixed Social Connections

### Screen 1: Settings Main Menu

```
┌─────────────────────────────────┐
│  Agent Settings         [×]     │
├─────────────────────────────────┤
│                                 │
│  👤 Identity                    │
│  Display Name: [Your Name]      │
│                                 │
│  🧠 AI Persona                  │
│  ○ Shadow   ○ Oracle   ○ Other  │
│                                 │
│  🎙️ Voice Interaction           │
│  AI Voice Replies: [toggle]     │
│  Voice Input (STT): [toggle]    │
│                                 │
│  🔗 Connected Accounts          │
│  ┌──────────────────────────┐   │
│  │ Manage Social Connections│ ← NEW
│  └──────────────────────────┘   │
│                                 │
│  [Secure Changes]               │
│                                 │
└─────────────────────────────────┘
```

### Screen 2: Social Connections Screen (Free Tier)

```
┌─────────────────────────────────┐
│  Connect Socials         [←]    │
├─────────────────────────────────┤
│                                 │
│ Select a platform to link with  │
│ Mistreal AI.                    │
│                                 │
│ ┌──────────────┬──────────────┐ │
│ │              │              │ │
│ │     🐦       │      💬      │ │
│ │              │              │ │
│ │  X (Twitter) │  WhatsApp    │ │
│ │              │              │ │
│ └──────────────┴──────────────┘ │
│                                 │
│ ┌──────────────┬──────────────┐ │
│ │              │      🔒      │ │
│ │     📷       │      f       │ │
│ │              │              │ │
│ │  Instagram   │  Facebook    │ │
│ │   (Pro)      │   (Pro)      │ │
│ │              │              │ │
│ └──────────────┴──────────────┘ │
│                                 │
│ ┌──────────────┬──────────────┐ │
│ │              │      🔒      │ │
│ │      in      │      in      │ │
│ │              │              │ │
│ │  LinkedIn    │  Telegram    │ │
│ │   (Pro)      │   (Pro)      │ │
│ │              │              │ │
│ └──────────────┴──────────────┘ │
│                                 │
└─────────────────────────────────┘
```

### Screen 3: Instagram OAuth (Clicking Instagram Card)

**Step 3a: Browser Redirects to Instagram**
```
User clicks Instagram card
  ↓
Browser opens (user doesn't see app)
  ↓
┌────────────────────────────────────┐
│  instagram.com                     │
├────────────────────────────────────┤
│                                    │
│  📷 Instagram                      │
│                                    │
│  Login to your account             │
│                                    │
│  Email/Phone: [____________]       │
│  Password:    [____________]       │
│                                    │
│           [Log In]                 │
│                                    │
│  OR                                │
│                                    │
│  [fb] [google] [apple]             │
│                                    │
└────────────────────────────────────┘
```

**Step 3b: Instagram Shows Permission Screen**
```
┌────────────────────────────────────┐
│  instagram.com                     │
├────────────────────────────────────┤
│                                    │
│  Mistreal AI is requesting access  │
│  to your Instagram Account         │
│                                    │
│  ✓ View your profile              │
│  ✓ View your posts                 │
│  ✓ View your followers             │
│                                    │
│  This will allow:                  │
│  - Syncing your posts to Mistreal  │
│  - Displaying feed content         │
│  - Analyzing trends                │
│                                    │
│  [Authorize] [Cancel]              │
│                                    │
└────────────────────────────────────┘
```

### Screen 4: Back to App - Success

**After user clicks "Authorize":**

```
Instagram redirects to app with success
  ↓
┌─────────────────────────────────┐
│  Connect Socials         [←]    │
├─────────────────────────────────┤
│                                 │
│ ✅ Instagram Connected!         │
│                                 │
│ [Platform Cards - Same View]    │
│                                 │
│ ┌──────────────┬──────────────┐ │
│ │              │              │ │
│ │     🐦       │      💬      │ │
│ │              │              │ │
│ │  X (Twitter) │  WhatsApp    │ │
│ │  Connected ✓ │  Connect     │ │
│ │              │              │ │
│ └──────────────┴──────────────┘ │
│                                 │
│ ┌──────────────┬──────────────┐ │
│ │              │      ✓       │ │
│ │     📷       │      f       │ │
│ │              │              │ │
│ │  Instagram   │  Facebook    │ │
│ │  Connected ✓ │   (Pro)      │ │
│ │              │              │ │
│ └──────────────┴──────────────┘ │
│                                 │
└─────────────────────────────────┘

Toast: "Instagram successfully connected! 🎉"
```

---

## Posts Appearing in Feeds Tab

Once connected, posts automatically sync when user opens Feeds tab:

### Feeds Screen After Sync

```
┌────────────────────────────────────┐
│  Feeds    ⟲ [sync icon]   [...]   │
├────────────────────────────────────┤
│ 📷 Instagram | 🐦 Twitter | More ▼ │
├────────────────────────────────────┤
│                                    │
│  📷 Instagram • @yourname          │
│  ────────────────────────────      │
│  Beautiful sunset at the beach! 🌅 │
│                                    │
│  ┌──────────────────────────────┐  │
│  │                              │  │
│  │    [Instagram Photo]         │  │
│  │                              │  │
│  └──────────────────────────────┘  │
│                                    │
│  ❤️ 234  💬 12  📤                 │
│  "Amazing shots!" "Love it!" ...  │
│                                    │
│ ─────────────────────────────────  │
│                                    │
│  🐦 X / Twitter • @yourname        │
│  ────────────────────────────      │
│  Just shipped v2.0! 🚀 #tech      │
│                                    │
│  ❤️ 1.2K  🔄 456  💬 89           │
│                                    │
│ ─────────────────────────────────  │
│                                    │
│  💬 WhatsApp                       │
│  ────────────────────────────      │
│  "Hey, how are you doing?"        │
│  mom - 2 hours ago                │
│                                    │
│  [Reply] [Forward] [Voice]        │
│                                    │
└────────────────────────────────────┘
```

---

## Disconnecting a Platform

### Disconnect Flow

```
Long-press on connected platform card
  ↓
┌─────────────────────────────────┐
│  Disconnect Instagram?          │
├─────────────────────────────────┤
│                                 │
│  This will:                     │
│  • Stop syncing your posts      │
│  • Remove Instagram data        │
│  • Clear access token           │
│                                 │
│      [Cancel] [Disconnect]      │
│                                 │
└─────────────────────────────────┘

✅ Instagram disconnected!
```

---

## Error States

### Error 1: No Internet

```
┌─────────────────────────────────┐
│  Connect Socials         [←]    │
├─────────────────────────────────┤
│                                 │
│ ⚠️  Failed to load platforms     │
│                                 │
│    Check your connection        │
│                                 │
│          [Try Again]            │
│                                 │
└─────────────────────────────────┘
```

### Error 2: Missing Credentials (Backend)

```
Toast: ❌ "Instagram Client ID not configured"
      (Admin should check .env)
```

### Error 3: User Cancels OAuth

```
Redirects back to app with error param
  ↓
Toast: "Instagram connection cancelled"
```

### Error 4: Invalid Account (Instagram Side)

```
Instagram shows error about account not eligible
  ↓
User sees redirect with error
  ↓
Toast: "Failed to connect: Account doesn't meet requirements"
```

---

## Premium Tier Difference

### Free Tier (2 platforms)
```
✓ Twitter / X
✓ WhatsApp

(Others locked with 🔒 icon)
```

### Premium Tier (all platforms)
```
✓ Twitter / X
✓ WhatsApp
✓ Instagram
✓ Facebook
✓ LinkedIn
✓ Telegram
✓ Reddit  (coming soon)
```

---

## What's Behind the Magic

When user clicks "Connect Instagram":

1. **Frontend** → Gets `deviceId` from Android Settings
2. **Frontend** → Opens browser: `https://backend.com/api/social/connect/instagram?deviceId=ABC123`
3. **Backend** → Generates OAuth URL with state including deviceId
4. **Backend** → Redirects browser to `https://api.instagram.com/oauth/authorize?...&state=base64(deviceId)`
5. **Instagram** → User logs in and grants permission
6. **Instagram** → Redirects to `https://backend.com/api/social/callback?code=XYZ&state=ABC123`
7. **Backend** → Decodes state (gets deviceId), exchanges code for token
8. **Backend** → Saves token to database `User.instagramAccessToken = token`
9. **Backend** → Redirects to `mistreal://social-connected?platform=instagram&success=true`
10. **Android App** → Detects deep link, shows success message
11. **Feeds Sync** → Next time user opens Feeds, calls `/api/social/sync`
12. **Backend** → Fetches from all connected platforms, returns unified posts
13. **Feeds Tab** → Displays posts with platform icons/colors

---

## Common Issues & Solutions

| Issue | Cause | Solution |
|-------|-------|----------|
| "App not configured" | Missing OAuth credentials | Get credentials, update `.env` |
| "Invalid redirect URL" | URL mismatch | Check URL in platform dashboard |
| No posts showing | Platform not connected or sync failed | Reconnect platform, check logs |
| "Couldn't find app" | Deleted app from platform | Create new app, get new credentials |
| Token expired | Instagram/Facebook tokens expire | Implement token refresh logic |

---

## Expected Timeline

- **Setup**: 1-2 hours (getting credentials)
- **Testing basic OAuth**: 10-15 minutes
- **Full deployment**: <5 minutes
- **Posting to socials**: 2-4 hours (if implemented)
- **Token refresh**: 1-2 hours (if needed)
