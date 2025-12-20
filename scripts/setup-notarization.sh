#!/bin/bash
#
# setup-notarization.sh - Interactive setup for Apple notarization credentials
#
# This script helps you configure the credentials needed for notarizing
# your app with Apple. Run this once before using sign-and-notarize.sh.
#
set -e

KEYCHAIN_PROFILE="${KEYCHAIN_PROFILE:-GalaxySim-notarize}"

echo "=== Apple Notarization Setup ==="
echo ""
echo "This script will store your Apple Developer credentials securely"
echo "in the macOS Keychain for notarization."
echo ""
echo "You will need:"
echo "  1. Your Apple ID (email)"
echo "  2. Your Team ID (from developer.apple.com/account)"
echo "  3. An app-specific password (from appleid.apple.com)"
echo ""
echo "─────────────────────────────────────────────────────────────"
echo ""

# Check for existing credentials
if xcrun notarytool history --keychain-profile "$KEYCHAIN_PROFILE" &>/dev/null 2>&1; then
    echo "Existing credentials found for profile: $KEYCHAIN_PROFILE"
    read -p "Do you want to replace them? (y/N) " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        echo "Keeping existing credentials."
        exit 0
    fi
fi

echo ""
echo "Step 1: Enter your Apple ID"
echo "  This is the email address you use for your Apple Developer account."
read -p "  Apple ID: " APPLE_ID

echo ""
echo "Step 2: Enter your Team ID"
echo "  Find this at: developer.apple.com/account > Membership"
echo "  It looks like: XXXXXXXXXX (10 characters)"
read -p "  Team ID: " TEAM_ID

echo ""
echo "Step 3: Enter your App-Specific Password"
echo "  Create one at: appleid.apple.com > Sign-In and Security > App-Specific Passwords"
echo "  Click '+' to generate a new password for 'Galaxy Simulator Notarization'"
echo "  It looks like: xxxx-xxxx-xxxx-xxxx"
echo ""
read -s -p "  App-Specific Password (hidden): " APP_PASSWORD
echo ""

echo ""
echo "Storing credentials in Keychain..."
echo ""

xcrun notarytool store-credentials "$KEYCHAIN_PROFILE" \
    --apple-id "$APPLE_ID" \
    --team-id "$TEAM_ID" \
    --password "$APP_PASSWORD"

echo ""
echo "✓ Credentials stored successfully!"
echo ""
echo "You can now run: ./sign-and-notarize.sh"
echo ""
echo "To verify your credentials work:"
echo "  xcrun notarytool history --keychain-profile $KEYCHAIN_PROFILE"
