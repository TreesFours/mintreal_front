# Backend Implementation - Messages & Social Contacts

## Overview

Backend needs to:
1. Fetch contacts from each social platform
2. Track their online status
3. Return unified unread messages with status
4. Filter by user tier (free vs premium)

---

## Database Schema Updates

### Add Contact Status Tracking

```typescript
// backend/src/models/socialContactModel.ts

interface SocialContact {
  id: string;
  deviceId: string;
  platform: string; // "twitter", "whatsapp", "instagram", etc.
  platformUserId: string; // Contact's ID on their platform
  name: string;
  avatar: string | null;
  unreadCount: number;
  isOnline: boolean;
  lastSeenAt: Date | null; // Timestamp when they were last active
  statusMessage: string | null; // "Available", "At lunch", etc.
  hasNewMessage: boolean;
  syncedAt: Date; // When we last synced their info
}

interface UnreadMessage {
  id: string;
  deviceId: string;
  platform: string;
  senderId: string;
  senderName: string;
  content: string;
  timestamp: Date;
  isRead: boolean;
  senderIsOnline: boolean;
  senderLastSeen: Date | null;
}
```

### Sequelize Models

```typescript
// backend/src/models/contactModel.ts
import { DataTypes, Model } from 'sequelize';
import { sequelize } from '../db';

export class SocialContact extends Model {
  public id!: string;
  public deviceId!: string;
  public platform!: string;
  public platformUserId!: string;
  public name!: string;
  public avatar!: string | null;
  public unreadCount!: number;
  public isOnline!: boolean;
  public lastSeenAt!: Date | null;
  public statusMessage!: string | null;
  public syncedAt!: Date;
}

SocialContact.init({
  id: { type: DataTypes.UUID, primaryKey: true, defaultValue: DataTypes.UUIDV4 },
  deviceId: { type: DataTypes.STRING, allowNull: false },
  platform: { type: DataTypes.STRING, allowNull: false }, // twitter, whatsapp, etc.
  platformUserId: { type: DataTypes.STRING, allowNull: false },
  name: { type: DataTypes.STRING, allowNull: false },
  avatar: { type: DataTypes.STRING, allowNull: true },
  unreadCount: { type: DataTypes.INTEGER, defaultValue: 0 },
  isOnline: { type: DataTypes.BOOLEAN, defaultValue: false },
  lastSeenAt: { type: DataTypes.DATE, allowNull: true },
  statusMessage: { type: DataTypes.TEXT, allowNull: true },
  syncedAt: { type: DataTypes.DATE, defaultValue: DataTypes.NOW }
}, {
  sequelize,
  tableName: 'SocialContacts'
});
```

---

## API Endpoints

### 1. Get Available AIs (Tier-Filtered)

**Endpoint:** `GET /api/ai/models`

```typescript
router.get('/api/ai/models', async (req: Request, res: Response) => {
  const { deviceId } = req.query;
  
  try {
    const user = await User.findOne({ where: { deviceId } });
    if (!user) {
      return res.status(404).json({ error: 'User not found' });
    }

    const allModels = [
      // Free tier models
      {
        id: 'dynamic',
        name: 'Dynamic (Best Fit)',
        provider: 'mistreal',
        tier: 'Free',
        isPro: false
      },
      {
        id: 'gpt-3.5',
        name: 'GPT-3.5 Turbo',
        provider: 'openai',
        tier: 'Free',
        isPro: false
      },
      // Premium tier models
      {
        id: 'gpt-4',
        name: 'GPT-4 (Advanced)',
        provider: 'openai',
        tier: 'Premium',
        isPro: true
      },
      {
        id: 'claude-3',
        name: 'Claude 3 Opus',
        provider: 'anthropic',
        tier: 'Premium',
        isPro: true
      }
    ];

    // Filter based on user tier
    const filteredModels = user.isPro 
      ? allModels 
      : allModels.filter(m => m.tier === 'Free');

    res.json(filteredModels);
  } catch (error: any) {
    res.status(500).json({ error: error.message });
  }
});
```

---

### 2. Get Social Contacts (With Status)

**Endpoint:** `GET /api/social/contacts`

```typescript
import { TwitterOAuth } from '../services/socialPlatforms/twitterAuth';
import { InstagramOAuth } from '../services/socialPlatforms/instagramAuth';
import { WhatsAppOAuth } from '../services/socialPlatforms/whatsappAuth';

router.get('/api/social/contacts', async (req: Request, res: Response) => {
  const { deviceId, platform } = req.query;
  
  if (!deviceId || !platform) {
    return res.status(400).json({ error: 'deviceId and platform required' });
  }

  try {
    const user = await User.findOne({ where: { deviceId } });
    if (!user) {
      return res.status(404).json({ error: 'User not found' });
    }

    // Check if user has access to this platform (tier check)
    const freeOnlyPlatforms = ['twitter', 'x', 'whatsapp'];
    if (!user.isPro && !freeOnlyPlatforms.includes(platform.toString().toLowerCase())) {
      return res.status(403).json({ error: 'Platform requires Premium subscription' });
    }

    // Get token for this platform
    const token = getTokenForPlatform(user, platform as string);
    if (!token) {
      return res.status(400).json({ error: `${platform} not connected` });
    }

    // Fetch contacts from platform
    let contacts: any[] = [];
    switch ((platform as string).toLowerCase()) {
      case 'twitter':
      case 'x':
        contacts = await TwitterOAuth.fetchFollowing(token);
        break;
      case 'whatsapp':
        contacts = await WhatsAppOAuth.fetchContacts(token);
        break;
      case 'instagram':
        contacts = await InstagramOAuth.fetchFollowers(token);
        break;
      // ... other platforms
    }

    // Enhance contacts with status info
    const enhancedContacts = await Promise.all(
      contacts.map(async (contact) => {
        // Get unread count from database
        const unreadCount = await getUnreadCountForContact(deviceId as string, platform as string, contact.id);
        
        // Get online status from cache or platform API
        const statusInfo = await getContactStatus(platform as string, contact.id);

        return {
          id: contact.id,
          name: contact.name,
          platform: platform,
          unreadCount: unreadCount || 0,
          isOnline: statusInfo.isOnline,
          lastSeen: statusInfo.lastSeen, // "5 minutes ago", etc.
          statusMessage: statusInfo.statusMessage,
          avatar: contact.avatar || contact.profileImage
        };
      })
    );

    res.json({ success: true, contacts: enhancedContacts });
  } catch (error: any) {
    console.error(`Failed to fetch ${platform} contacts:`, error);
    res.status(500).json({ error: error.message });
  }
});

// Helper to get correct token
function getTokenForPlatform(user: any, platform: string): string | null {
  const platformLower = platform.toLowerCase();
  if (platformLower === 'twitter' || platformLower === 'x') {
    return user.twitterAccessToken;
  } else if (platformLower === 'whatsapp' || platformLower === 'whatsapp_business') {
    return user.whatsappAccessToken;
  } else if (platformLower === 'instagram') {
    return user.instagramAccessToken;
  } else if (platformLower === 'facebook') {
    return user.facebookAccessToken;
  } else if (platformLower === 'linkedin') {
    return user.linkedinAccessToken;
  }
  return null;
}

// Get contact's online status
async function getContactStatus(platform: string, contactId: string) {
  // This could be from:
  // 1. Platform API (if they provide real-time status)
  // 2. Webhook updates stored in cache/database
  // 3. Last message timestamp
  
  const cacheKey = `contact_${platform}_${contactId}`;
  const cached = await redis.get(cacheKey);
  
  if (cached) {
    return JSON.parse(cached);
  }

  // Fallback: assume online based on recent message
  const recentMessage = await UnreadMessage.findOne({
    where: { senderId: contactId, platform },
    order: [['timestamp', 'DESC']]
  });

  const lastSeen = recentMessage 
    ? formatLastSeen(recentMessage.timestamp)
    : 'Last seen recently';

  return {
    isOnline: false,
    lastSeen: lastSeen,
    statusMessage: null
  };
}

// Get unread count for a contact
async function getUnreadCountForContact(deviceId: string, platform: string, contactId: string) {
  const count = await UnreadMessage.count({
    where: { deviceId, platform, senderId: contactId, isRead: false }
  });
  return count;
}
```

---

### 3. Get Unified Unread Messages

**Endpoint:** `GET /api/social/unread`

```typescript
router.get('/api/social/unread', async (req: Request, res: Response) => {
  const { deviceId } = req.query;

  if (!deviceId) {
    return res.status(400).json({ error: 'deviceId required' });
  }

  try {
    const user = await User.findOne({ where: { deviceId } });
    if (!user) {
      return res.status(404).json({ error: 'User not found' });
    }

    // Get unread messages, filtering by tier
    const freeOnlyPlatforms = ['twitter', 'x', 'whatsapp'];
    const allowedPlatforms = user.isPro 
      ? ['twitter', 'whatsapp', 'instagram', 'facebook', 'linkedin', 'telegram']
      : freeOnlyPlatforms;

    const unreadMessages = await UnreadMessage.findAll({
      where: { 
        deviceId: deviceId,
        isRead: false,
        platform: { [Op.in]: allowedPlatforms }
      },
      order: [['timestamp', 'DESC']],
      limit: 50
    });

    // Enhance with sender status
    const enhancedMessages = await Promise.all(
      unreadMessages.map(async (msg) => {
        const senderStatus = await getContactStatus(msg.platform, msg.senderId);
        
        return {
          id: msg.id,
          sender: msg.senderName,
          platform: msg.platform,
          text: msg.content.substring(0, 100), // Preview
          timestamp: msg.timestamp.toISOString(),
          isOnline: senderStatus.isOnline,
          lastSeen: senderStatus.lastSeen
        };
      })
    );

    res.json({ success: true, unreadItems: enhancedMessages });
  } catch (error: any) {
    res.status(500).json({ error: error.message });
  }
});
```

---

## Webhook Handlers (For Real-Time Status)

### WhatsApp Status Update

```typescript
// backend/src/routes/webhookRoutes.ts
router.post('/webhook/whatsapp/status', async (req: Request, res: Response) => {
  const { userId, status, timestamp } = req.body;

  try {
    // Update contact status in database
    const contact = await SocialContact.update(
      {
        isOnline: status === 'online',
        lastSeenAt: status === 'offline' ? new Date(timestamp) : null
      },
      { where: { platformUserId: userId, platform: 'whatsapp' } }
    );

    // Cache for quick access
    await redis.set(
      `contact_whatsapp_${userId}`,
      JSON.stringify({
        isOnline: status === 'online',
        lastSeen: status === 'offline' ? formatLastSeen(new Date(timestamp)) : null
      }),
      'EX', 300 // expire after 5 minutes
    );

    res.json({ success: true });
  } catch (error: any) {
    console.error('WhatsApp status update error:', error);
    res.status(500).json({ error: error.message });
  }
});
```

### Twitter Online Status (if available)

```typescript
router.post('/webhook/twitter/status', async (req: Request, res: Response) => {
  const { userId, status } = req.body;

  try {
    await redis.set(
      `contact_twitter_${userId}`,
      JSON.stringify({
        isOnline: status === 'online',
        lastSeen: formatLastSeen(new Date())
      }),
      'EX', 300
    );

    res.json({ success: true });
  } catch (error: any) {
    res.status(500).json({ error: error.message });
  }
});
```

---

## Helper Functions

```typescript
// Format timestamp to "X minutes ago" format
function formatLastSeen(date: Date): string {
  const seconds = Math.floor((Date.now() - date.getTime()) / 1000);
  const minutes = Math.floor(seconds / 60);
  const hours = Math.floor(minutes / 60);
  const days = Math.floor(hours / 24);

  if (seconds < 60) return 'Just now';
  if (minutes < 60) return `${minutes}m ago`;
  if (hours < 24) return `${hours}h ago`;
  if (days === 1) return 'Yesterday';
  if (days < 7) return `${days}d ago`;
  return date.toLocaleDateString();
}

// Get unread count per platform
async function getUnreadCountByPlatform(deviceId: string, platform: string) {
  return await UnreadMessage.count({
    where: { deviceId, platform, isRead: false }
  });
}

// Mark messages as read
async function markAsRead(deviceId: string, platform: string, senderId: string) {
  await UnreadMessage.update(
    { isRead: true },
    { where: { deviceId, platform, senderId } }
  );
}
```

---

## Tier-Based Access Control

```typescript
// Middleware to check tier access
const checkTierAccess = (req: Request, res: Response, next: Function) => {
  const { platform } = req.query;
  const user = req.user; // Assuming auth middleware sets this

  const freeOnlyPlatforms = ['twitter', 'x', 'whatsapp'];
  const requestedPlatform = (platform as string).toLowerCase();

  if (!user.isPro && !freeOnlyPlatforms.includes(requestedPlatform)) {
    return res.status(403).json({
      error: `${platform} requires Premium subscription`,
      requiresUpgrade: true
    });
  }

  next();
};

// Apply middleware
router.get('/api/social/contacts', checkTierAccess, async (req, res) => {
  // ... endpoint logic
});
```

---

## Data Flow Diagram

```
User Opens Messages Tab
  ↓
App calls: GET /api/ai/models?deviceId=ABC123
  ↓
Backend:
  - Finds User (deviceId)
  - Filters AIs by tier
  - Returns [Dynamic, GPT-3.5] for free, or [All AIs] for premium
  ↓
App calls: GET /api/social/platforms?deviceId=ABC123
  ↓
Backend:
  - Returns platforms user has connected
  - Filters by tier (free sees Twitter + WhatsApp only)
  ↓
User clicks Platform (e.g., "WhatsApp")
  ↓
App calls: GET /api/social/contacts?deviceId=ABC123&platform=whatsapp
  ↓
Backend:
  - Gets user.whatsappAccessToken
  - Calls WhatsApp API to fetch contacts
  - Gets online status from cache/webhooks
  - Returns: [{name, isOnline, lastSeen, unreadCount}, ...]
  ↓
User clicks "Inbox"
  ↓
App calls: GET /api/social/unread?deviceId=ABC123
  ↓
Backend:
  - Queries UnreadMessage table
  - Filters by tier-allowed platforms
  - Adds sender status info
  - Returns: [{sender, platform, text, isOnline, lastSeen}, ...]
```

---

## Testing

### Manual Testing

```bash
# Test AI models endpoint
curl "http://localhost:3000/api/ai/models?deviceId=test123"

# Test contacts endpoint
curl "http://localhost:3000/api/social/contacts?deviceId=test123&platform=whatsapp"

# Test unread messages
curl "http://localhost:3000/api/social/unread?deviceId=test123"
```

### Expected Responses

**AI Models (Free User):**
```json
[
  {"id": "dynamic", "name": "Dynamic", "tier": "Free"},
  {"id": "gpt-3.5", "name": "GPT-3.5", "tier": "Free"}
]
```

**AI Models (Premium User):**
```json
[
  {"id": "dynamic", "name": "Dynamic", "tier": "Free"},
  {"id": "gpt-3.5", "name": "GPT-3.5", "tier": "Free"},
  {"id": "gpt-4", "name": "GPT-4", "tier": "Premium"},
  {"id": "claude-3", "name": "Claude 3", "tier": "Premium"}
]
```

**Contacts:**
```json
{
  "success": true,
  "contacts": [
    {
      "id": "w_123",
      "name": "John Doe",
      "platform": "whatsapp",
      "unreadCount": 3,
      "isOnline": true,
      "lastSeen": null,
      "avatar": "https://..."
    }
  ]
}
```

---

## Summary

- ✅ Tier-based filtering for AIs and social platforms
- ✅ Real-time contact status tracking
- ✅ Unified unread messages from all platforms
- ✅ Webhook support for live updates
- ✅ Proper error handling for access control
