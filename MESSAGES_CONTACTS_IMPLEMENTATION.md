# Messages & Social Contacts UI Implementation

## Overview

The Messages screen now has:
1. **Tier-based AI Selection** - Different AIs for free vs premium users
2. **Social Contact Integration** - Twitter friends, WhatsApp contacts, etc. with online status
3. **Unified Unread Messages** - Recent messages from all platforms in one inbox
4. **Live Status Updates** - Shows "Active", "Last seen X mins ago", etc.

---

## Architecture

### UI Structure (Drawer)

```
Left Sidebar (Icons):
├─ 🧠 AI (Always available)
├─ 🐦 Twitter (Free tier)
├─ 💬 WhatsApp (Free tier)
├─ 📷 Instagram (Premium only)
├─ f Facebook (Premium only)
├─ 📮 Inbox (Unread - unified)

Right Panel (Content):
├─ When "AI" selected:
│  └─ List of available AIs with tier badges
├─ When Platform selected:
│  └─ List of contacts for that platform
└─ When "Inbox" selected:
   └─ Recent unread messages from all platforms
```

### Top Bar Status

```
┌─────────────────────────────────┐
│ [≡] John Doe          ⟳ ⚙ 💣    │
│     Last seen 5 mins │
└─────────────────────────────────┘
```

Shows:
- Contact name (or AI name)
- Status: "Active" (green), "Last seen X ago", or "Offline"

---

## Frontend Changes Made

### 1. **Data Models Updated**

**SocialContact** (now includes status):
```kotlin
data class SocialContact(
    val id: String,
    val name: String,
    val platform: String,        // "twitter", "whatsapp", "instagram", etc.
    val unreadCount: Int,
    val isOnline: Boolean = false, // New: online status
    val lastSeen: String? = null,  // New: "5 minutes ago", "2 hours ago"
    val statusMessage: String? = null,  // New: custom status
    val avatar: String? = null     // New: profile picture URL
)

data class UnreadItem(
    val id: String,
    val sender: String,
    val platform: String,
    val text: String,
    val timestamp: String,
    val isOnline: Boolean = false,      // New
    val lastSeen: String? = null        // New
)
```

### 2. **ChatViewModel Enhanced**

Added states to track:
```kotlin
// Current chat partner info
val currentChatPartner: State<String>          // "John Doe"
val currentChatPartnerStatus: State<String>    // "Active" or "Last seen 2h ago"
val currentChatPartnerPlatform: State<String>  // "whatsapp", "twitter", "ai"
```

### 3. **Tier-Based Filtering**

**AI Models** - Filtered by tier when displayed:
```kotlin
if (isPro) {
    // Premium: Show all AIs
    showAllAIs()
} else {
    // Free: Only show models with tier = "Free"
    showFreeAIs()
}
```

**Social Platforms** - Filtered by tier:
```kotlin
if (isPro) {
    // Premium: All platforms
    showPlatforms(["twitter", "whatsapp", "instagram", "facebook", "linkedin", ...])
} else {
    // Free: Only Twitter & WhatsApp
    showPlatforms(["twitter", "whatsapp"])
}
```

### 4. **Contact Status Display**

Drawer shows:
- ✅ Green dot if contact is online
- ⏱️ "Last seen X mins ago" if offline
- 🔴 Red badge count if unread messages

UnreadInbox shows:
- Platform icon (🐦, 💬, 📷, f, etc.)
- Sender name + online status
- Message preview
- "Last seen" timestamp

---

## Backend API Requirements

### Endpoint: `GET /api/social/contacts`

**Request:**
```
GET /api/social/contacts?deviceId=ABC123&platform=whatsapp
```

**Response:**
```json
{
  "success": true,
  "contacts": [
    {
      "id": "w_12345",
      "name": "John Doe",
      "platform": "whatsapp",
      "unreadCount": 3,
      "isOnline": true,
      "lastSeen": null,
      "statusMessage": "Available",
      "avatar": "https://..."
    },
    {
      "id": "w_67890",
      "name": "Jane Smith",
      "platform": "whatsapp",
      "unreadCount": 0,
      "isOnline": false,
      "lastSeen": "5 minutes ago",
      "statusMessage": null,
      "avatar": "https://..."
    }
  ]
}
```

### Endpoint: `GET /api/social/unread`

**Request:**
```
GET /api/social/unread?deviceId=ABC123
```

**Response:**
```json
{
  "success": true,
  "unreadItems": [
    {
      "id": "msg_1",
      "sender": "John Doe",
      "platform": "whatsapp",
      "text": "Hey! How are you?",
      "timestamp": "2024-08-02T10:30:00Z",
      "isOnline": true,
      "lastSeen": null
    },
    {
      "id": "msg_2",
      "sender": "jane_twitter",
      "platform": "twitter",
      "text": "@you Great post! 🚀",
      "timestamp": "2024-08-02T09:15:00Z",
      "isOnline": false,
      "lastSeen": "2 hours ago"
    },
    {
      "id": "msg_3",
      "sender": "Mike",
      "platform": "instagram",
      "text": "Liked your story",
      "timestamp": "2024-08-02T08:00:00Z",
      "isOnline": false,
      "lastSeen": "Yesterday"
    }
  ]
}
```

### Endpoint: `GET /api/user/models` or `/api/ai/models`

**Request:**
```
GET /api/ai/models?deviceId=ABC123&isPro=true
```

**Response:**
```json
[
  {
    "id": "gpt-4",
    "name": "GPT-4 (Advanced)",
    "provider": "openai",
    "isPro": true,
    "tier": "Premium"
  },
  {
    "id": "claude-3",
    "name": "Claude 3 (Expert)",
    "provider": "anthropic",
    "isPro": true,
    "tier": "Premium"
  },
  {
    "id": "mistral",
    "name": "Mistral AI",
    "provider": "mistral",
    "isPro": false,
    "tier": "Free"
  }
]
```

---

## Webhook Integration (For Real-Time Status)

The backend should use webhooks from platforms to update contact status in real-time:

### When User Comes Online
```
Platform (WhatsApp/Twitter/Instagram) → Backend Webhook
→ Updates: User.isOnline = true, User.lastSeen = null
→ App polls → Shows "Active"
```

### When User Goes Offline
```
Platform → Backend Webhook
→ Updates: User.isOnline = false, User.lastSeen = "2 minutes ago"
→ App polls → Shows status
```

### Implementation Path

1. **Platform Provides Webhooks** (most do):
   - WhatsApp: Status webhook
   - Instagram: Message webhooks
   - Twitter: User presence API

2. **Backend Stores Status**:
   ```typescript
   // In User/Contact model
   lastSeenAt: Date
   isOnline: boolean
   statusMessage?: string
   ```

3. **API Returns Status**:
   ```typescript
   lastSeen: displayLastSeen(lastSeenAt),  // "5 mins ago"
   isOnline: isOnline
   ```

---

## User Flow

### 1. Open Messages Tab

```
App loads ChatScreen
  ↓
ChatViewModel.fetchAvailableModels() → Shows tier-filtered AIs
ChatViewModel.fetchAvailablePlatforms() → Shows tier-filtered socials
  ↓
Drawer shows:
- 🧠 AI (all AIs, filtered by tier)
- 🐦 Twitter (free users see this)
- 💬 WhatsApp (free users see this)
- 📷 Instagram (only premium users see this)
- 📮 Inbox (unified unread)
```

### 2. Click on "Inbox"

```
User clicks "Inbox" icon
  ↓
ChatViewModel.fetchUnread()
  ↓
/api/social/unread?deviceId=ABC returns:
[
  {sender: "John", platform: "whatsapp", text: "Hey!", isOnline: true},
  {sender: "jane", platform: "twitter", text: "Great post!", isOnline: false, lastSeen: "2h ago"}
]
  ↓
UI displays unified list with platform icons + status
```

### 3. Click on "WhatsApp" Platform

```
User clicks 💬 (WhatsApp)
  ↓
ChatViewModel.fetchContacts("whatsapp")
  ↓
/api/social/contacts?platform=whatsapp returns:
[
  {name: "John", unreadCount: 3, isOnline: true},
  {name: "Jane", unreadCount: 0, isOnline: false, lastSeen: "5m ago"}
]
  ↓
UI shows WhatsApp contact list with online status
```

### 4. Click on a Contact

```
User clicks "John Doe" WhatsApp contact
  ↓
viewModel.switchChat("John Doe", "whatsapp")
  ↓
TopBar updates:
┌──────────────────────────┐
│ [≡] John Doe           │
│     Active (green dot)  │
└──────────────────────────┘
  ↓
Chat history loads for John Doe
```

### 5. Switch to Twitter

```
User clicks 🐦 (Twitter)
  ↓
ChatViewModel.fetchContacts("twitter")
  ↓
Shows Twitter friends list
```

### 6. Premium User Experience

```
Same flow, but drawer shows:
- 🧠 AI (ALL AIs - not filtered)
- 🐦 Twitter
- 💬 WhatsApp  
- 📷 Instagram
- f Facebook
- 🤖 LinkedIn
- 📮 Inbox
```

---

## Status Updates in Real-Time

### Polling Method (Current)

```kotlin
// When chat screen is open
LaunchedEffect(currentChatPartner) {
    while (isActive) {
        delay(5000)  // Poll every 5 seconds
        viewModel.updateContactStatus(currentChatPartner)
    }
}
```

### Webhook Method (Future)

```kotlin
// Listen for WebSocket updates
websocket.onMessage { event ->
    when (event.type) {
        "user_online" -> {
            updateContactStatus(event.userId, "Active")
        }
        "user_offline" -> {
            updateContactStatus(event.userId, "Last seen ${event.timestamp}")
        }
    }
}
```

---

## Error Handling

### If Platform Not Connected
```
ContactsResponse {
  success: false,
  error: "Platform not connected. Please connect in Settings."
}
```

### If Free User Tries Premium Platform
```
Only show in drawer if user.isPro == true
Or show with 🔒 lock icon + "Upgrade to Premium"
```

---

## Summary

| Feature | Free Tier | Premium Tier |
|---------|-----------|--------------|
| AI Selection | Limited (Free tier AIs only) | All AIs |
| Social Platforms | Twitter + WhatsApp only | All platforms |
| Contact Status | Show online/offline | Show online/offline |
| Unified Inbox | ✅ Yes | ✅ Yes |
| Message Sync | ✅ Yes (free platforms) | ✅ Yes (all platforms) |
| Webhooks | ✅ Yes | ✅ Yes |
