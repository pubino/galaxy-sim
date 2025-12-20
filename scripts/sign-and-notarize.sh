#!/bin/bash
#
# sign-and-notarize.sh - Sign and notarize Galaxy Simulator for macOS distribution
#
# Prerequisites:
#   1. Apple Developer account enrolled in Apple Developer Program
#   2. Developer ID Application certificate installed in Keychain
#   3. App-specific password for notarization (create at appleid.apple.com)
#   4. Store credentials in Keychain (see setup instructions below)
#
# Setup (run once):
#   xcrun notarytool store-credentials "GalaxySim-notarize" \
#       --apple-id "your-apple-id@email.com" \
#       --team-id "YOUR_TEAM_ID" \
#       --password "your-app-specific-password"
#
# Usage:
#   ./sign-and-notarize.sh
#   ./sign-and-notarize.sh --skip-notarize    # Sign only, skip notarization
#
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
DIST_DIR="$PROJECT_DIR/dist"
APP_NAME="Galaxy Simulator"
APP_PATH="$DIST_DIR/$APP_NAME.app"
DMG_PATH="$DIST_DIR/GalaxySim.dmg"
ZIP_PATH="$DIST_DIR/GalaxySim.zip"

# Configuration - UPDATE THESE VALUES
DEVELOPER_ID_APP="${DEVELOPER_ID_APP:-Developer ID Application: Your Name (TEAM_ID)}"
KEYCHAIN_PROFILE="${KEYCHAIN_PROFILE:-GalaxySim-notarize}"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo "=== Galaxy Simulator Signing & Notarization ==="
echo ""

# Check if app exists
if [ ! -d "$APP_PATH" ]; then
    echo -e "${RED}Error: App bundle not found at $APP_PATH${NC}"
    echo "Run create-app-bundle.sh first."
    exit 1
fi

# List available signing identities
echo "Available signing identities:"
security find-identity -v -p codesigning | grep "Developer ID Application" || true
echo ""

# Check if the specified identity exists
if ! security find-identity -v -p codesigning | grep -q "Developer ID Application"; then
    echo -e "${RED}Error: No 'Developer ID Application' certificate found in Keychain.${NC}"
    echo ""
    echo "To fix this:"
    echo "  1. Open Keychain Access"
    echo "  2. Ensure your Developer ID Application certificate is installed"
    echo "  3. The certificate must be valid and not expired"
    echo ""
    echo "To create a certificate:"
    echo "  1. Go to developer.apple.com/account"
    echo "  2. Certificates, IDs & Profiles > Certificates"
    echo "  3. Create a 'Developer ID Application' certificate"
    echo "  4. Download and double-click to install"
    exit 1
fi

# ============================================
# STEP 1: Sign the application
# ============================================
echo -e "${YELLOW}Step 1: Signing application...${NC}"

# Create entitlements file for hardened runtime
ENTITLEMENTS="$DIST_DIR/entitlements.plist"
cat > "$ENTITLEMENTS" << 'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>com.apple.security.cs.allow-jit</key>
    <true/>
    <key>com.apple.security.cs.allow-unsigned-executable-memory</key>
    <true/>
    <key>com.apple.security.cs.disable-library-validation</key>
    <true/>
</dict>
</plist>
EOF

# Sign all nested components first (for jpackage bundles with JRE)
echo "Signing nested components..."
find "$APP_PATH" -type f \( -name "*.dylib" -o -name "*.jnilib" -o -name "*.framework" \) -print0 2>/dev/null | \
while IFS= read -r -d '' file; do
    codesign --force --options runtime --timestamp \
        --sign "$DEVELOPER_ID_APP" \
        "$file" 2>/dev/null || true
done

# Sign any nested executables
find "$APP_PATH/Contents" -type f -perm +111 ! -name "launcher" -print0 2>/dev/null | \
while IFS= read -r -d '' file; do
    codesign --force --options runtime --timestamp \
        --sign "$DEVELOPER_ID_APP" \
        --entitlements "$ENTITLEMENTS" \
        "$file" 2>/dev/null || true
done

# Sign the main app bundle
echo "Signing main app bundle..."
codesign --force --deep --options runtime --timestamp \
    --sign "$DEVELOPER_ID_APP" \
    --entitlements "$ENTITLEMENTS" \
    "$APP_PATH"

# Verify signature
echo "Verifying signature..."
codesign --verify --deep --strict --verbose=2 "$APP_PATH"

echo -e "${GREEN}Signing complete!${NC}"
echo ""

# Check if we should skip notarization
if [ "$1" == "--skip-notarize" ]; then
    echo "Skipping notarization (--skip-notarize flag set)"
    exit 0
fi

# ============================================
# STEP 2: Create ZIP for notarization
# ============================================
echo -e "${YELLOW}Step 2: Creating ZIP archive for notarization...${NC}"

rm -f "$ZIP_PATH"
ditto -c -k --keepParent "$APP_PATH" "$ZIP_PATH"

echo "Created: $ZIP_PATH"
echo ""

# ============================================
# STEP 3: Submit for notarization
# ============================================
echo -e "${YELLOW}Step 3: Submitting for notarization...${NC}"
echo "This may take several minutes..."
echo ""

# Check if credentials are stored
if ! xcrun notarytool history --keychain-profile "$KEYCHAIN_PROFILE" &>/dev/null; then
    echo -e "${RED}Error: Keychain profile '$KEYCHAIN_PROFILE' not found.${NC}"
    echo ""
    echo "To store your credentials, run:"
    echo ""
    echo "  xcrun notarytool store-credentials \"$KEYCHAIN_PROFILE\" \\"
    echo "      --apple-id \"your-apple-id@email.com\" \\"
    echo "      --team-id \"YOUR_TEAM_ID\" \\"
    echo "      --password \"your-app-specific-password\""
    echo ""
    echo "To create an app-specific password:"
    echo "  1. Go to appleid.apple.com"
    echo "  2. Sign In & Security > App-Specific Passwords"
    echo "  3. Generate a new password for 'Galaxy Simulator Notarization'"
    echo ""
    echo "To find your Team ID:"
    echo "  1. Go to developer.apple.com/account"
    echo "  2. Membership details shows your Team ID"
    exit 1
fi

# Submit for notarization and wait
xcrun notarytool submit "$ZIP_PATH" \
    --keychain-profile "$KEYCHAIN_PROFILE" \
    --wait

# Get the submission ID from the last submission
SUBMISSION_ID=$(xcrun notarytool history --keychain-profile "$KEYCHAIN_PROFILE" 2>/dev/null | head -4 | tail -1 | awk '{print $1}')

if [ -n "$SUBMISSION_ID" ]; then
    echo ""
    echo "Notarization log:"
    xcrun notarytool log "$SUBMISSION_ID" --keychain-profile "$KEYCHAIN_PROFILE" || true
fi

echo ""

# ============================================
# STEP 4: Staple the notarization ticket
# ============================================
echo -e "${YELLOW}Step 4: Stapling notarization ticket...${NC}"

xcrun stapler staple "$APP_PATH"

echo -e "${GREEN}Stapling complete!${NC}"
echo ""

# ============================================
# STEP 5: Create distributable DMG (optional)
# ============================================
echo -e "${YELLOW}Step 5: Creating distributable DMG...${NC}"

rm -f "$DMG_PATH"

# Create a temporary directory for DMG contents
DMG_TEMP="$DIST_DIR/dmg-temp"
rm -rf "$DMG_TEMP"
mkdir -p "$DMG_TEMP"

# Copy the app
cp -R "$APP_PATH" "$DMG_TEMP/"

# Create a symbolic link to Applications folder
ln -s /Applications "$DMG_TEMP/Applications"

# Create the DMG
hdiutil create -volname "$APP_NAME" \
    -srcfolder "$DMG_TEMP" \
    -ov -format UDZO \
    "$DMG_PATH"

# Clean up temp directory
rm -rf "$DMG_TEMP"

# Sign the DMG
echo "Signing DMG..."
codesign --force --sign "$DEVELOPER_ID_APP" "$DMG_PATH"

# Notarize the DMG
echo "Notarizing DMG..."
xcrun notarytool submit "$DMG_PATH" \
    --keychain-profile "$KEYCHAIN_PROFILE" \
    --wait

# Staple the DMG
xcrun stapler staple "$DMG_PATH"

echo ""
echo -e "${GREEN}=== Complete! ===${NC}"
echo ""
echo "Distribution files:"
echo "  App:  $APP_PATH"
echo "  DMG:  $DMG_PATH"
echo ""
echo "The DMG is ready for distribution. Users can:"
echo "  1. Download the DMG"
echo "  2. Open it and drag the app to Applications"
echo "  3. Run the app without Gatekeeper warnings"
