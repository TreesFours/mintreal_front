#!/bin/bash
# SOCIAL_SETUP.sh - Quick setup script for platform credentials

echo "🚀 Mistreal Social Media Integration Setup"
echo "==========================================="
echo ""
echo "This script will help you gather platform credentials."
echo "You'll need developer accounts for each platform."
echo ""

# Check if .env exists
if [ ! -f "backend/.env" ]; then
    echo "Creating backend/.env from template..."
    cp backend/.env.example backend/.env
    echo "✅ Created backend/.env"
    echo ""
    echo "⚠️  NEXT: Edit backend/.env and fill in your credentials"
    echo ""
fi

echo ""
echo "📋 CREDENTIAL CHECKLIST:"
echo ""

echo "1️⃣  INSTAGRAM"
echo "   Go to: https://developers.facebook.com/"
echo "   - Sign in or create Meta Developer account"
echo "   - Create or select existing app"
echo "   - Add 'Instagram Graph API' product"
echo "   - Get Client ID & Secret from Settings → Basic"
echo "   - Add redirect URI: https://yourdomain/api/social/callback/instagram"
echo ""

echo "2️⃣  TWITTER"
echo "   Go to: https://developer.twitter.com/dashboard"
echo "   - Sign in to Twitter Developer Portal"
echo "   - Create 'OAuth 2.0' app"
echo "   - Enable User Context Permissions"
echo "   - Get Client ID & Secret from 'Keys and tokens'"
echo "   - Add callback URL in app settings"
echo ""

echo "3️⃣  FACEBOOK"
echo "   Go to: https://developers.facebook.com/"
echo "   - Use SAME app as Instagram"
echo "   - Add 'Facebook Login' product"
echo "   - Set Valid OAuth Redirect URIs"
echo "   - Get App Secret from Settings → Basic"
echo ""

echo "4️⃣  WHATSAPP"
echo "   Go to: https://www.whatsapp.com/business/"
echo "   - Sign up for WhatsApp Business"
echo "   - Complete phone verification"
echo "   - Get Phone Number ID from app dashboard"
echo "   - Also uses Facebook credentials (same app)"
echo ""

echo "5️⃣  TELEGRAM"
echo "   - Open Telegram app"
echo "   - Find @BotFather bot"
echo "   - Send: /newbot"
echo "   - Follow prompts"
echo "   - Copy bot token"
echo "   - (Optional) Set webhook URL for updates"
echo ""

echo "💾 After gathering credentials:"
echo "   1. Edit backend/.env"
echo "   2. Fill in all CLIENT_IDs, CLIENT_SECRETs, etc."
echo "   3. Deploy to Render"
echo "   4. Test with: curl http://localhost:3000/api/social/auth/instagram?deviceId=test"
echo ""

echo "📚 Full guide: See SOCIAL_INTEGRATION_GUIDE.md"
echo ""
