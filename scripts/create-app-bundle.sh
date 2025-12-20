#!/bin/bash
#
# create-app-bundle.sh - Create macOS .app bundle for Galaxy Simulator
#
# Usage: ./create-app-bundle.sh [--use-jpackage]
#
# Options:
#   --use-jpackage    Use jpackage (requires Java 14+, bundles JRE)
#   (default)         Create lightweight .app that uses system Java
#
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
DIST_DIR="$PROJECT_DIR/dist"
APP_NAME="GalaxySimulator"
DISPLAY_NAME="Galaxy Simulator"
BUNDLE_ID="edu.princeton.vanderbei.galaxy-sim"
VERSION="1.0.0"

echo "=== Creating macOS App Bundle ==="

# Ensure build exists
if [ ! -f "$DIST_DIR/GalaxySim.jar" ]; then
    echo "JAR not found. Running build first..."
    "$SCRIPT_DIR/build.sh"
fi

# Check for jpackage option
if [ "$1" == "--use-jpackage" ]; then
    echo "Using jpackage to create self-contained app..."

    # Check jpackage availability
    if ! command -v jpackage &> /dev/null; then
        echo "Error: jpackage not found. Requires Java 14+."
        exit 1
    fi

    rm -rf "$DIST_DIR/$APP_NAME.app"

    jpackage \
        --type app-image \
        --name "$APP_NAME" \
        --input "$DIST_DIR" \
        --main-jar GalaxySim.jar \
        --main-class astro.galaxy.Galaxy \
        --dest "$DIST_DIR" \
        --app-version "$VERSION" \
        --vendor "Vanderbei" \
        --mac-package-identifier "$BUNDLE_ID" \
        --mac-package-name "$DISPLAY_NAME" \
        --java-options "-Xmx512m"

    echo "Created: $DIST_DIR/$APP_NAME.app (with bundled JRE)"
else
    echo "Creating lightweight app bundle (requires system Java)..."

    APP_DIR="$DIST_DIR/$APP_NAME.app"
    CONTENTS_DIR="$APP_DIR/Contents"
    MACOS_DIR="$CONTENTS_DIR/MacOS"
    RESOURCES_DIR="$CONTENTS_DIR/Resources"

    # Clean and create structure
    rm -rf "$APP_DIR"
    mkdir -p "$MACOS_DIR" "$RESOURCES_DIR"

    # Copy JAR to Resources
    cp "$DIST_DIR/GalaxySim.jar" "$RESOURCES_DIR/"

    # Create Info.plist
    cat > "$CONTENTS_DIR/Info.plist" << EOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>CFBundleExecutable</key>
    <string>launcher</string>
    <key>CFBundleIconFile</key>
    <string>AppIcon</string>
    <key>CFBundleIdentifier</key>
    <string>$BUNDLE_ID</string>
    <key>CFBundleName</key>
    <string>$DISPLAY_NAME</string>
    <key>CFBundleDisplayName</key>
    <string>$DISPLAY_NAME</string>
    <key>CFBundlePackageType</key>
    <string>APPL</string>
    <key>CFBundleShortVersionString</key>
    <string>$VERSION</string>
    <key>CFBundleVersion</key>
    <string>$VERSION</string>
    <key>LSMinimumSystemVersion</key>
    <string>10.13</string>
    <key>NSHighResolutionCapable</key>
    <true/>
    <key>NSSupportsAutomaticGraphicsSwitching</key>
    <true/>
</dict>
</plist>
EOF

    # Create launcher script
    cat > "$MACOS_DIR/launcher" << 'EOF'
#!/bin/bash

# Find Java
if [ -n "$JAVA_HOME" ]; then
    JAVA="$JAVA_HOME/bin/java"
elif /usr/libexec/java_home &> /dev/null; then
    JAVA="$(/usr/libexec/java_home)/bin/java"
else
    JAVA="java"
fi

# Check Java is available
if ! command -v "$JAVA" &> /dev/null; then
    osascript -e 'display dialog "Java is required but not installed.\n\nPlease install Java from https://adoptium.net" buttons {"OK"} default button "OK" with icon stop with title "Galaxy Simulator"'
    exit 1
fi

# Get the directory where this script is located
DIR="$(cd "$(dirname "$0")/../Resources" && pwd)"

# Run the application
exec "$JAVA" \
    -Xmx512m \
    -Dapple.awt.application.name="Galaxy Simulator" \
    -Xdock:name="Galaxy Simulator" \
    -jar "$DIR/GalaxySim.jar"
EOF

    chmod +x "$MACOS_DIR/launcher"

    echo "Created: $APP_DIR"
    echo ""
    echo "Note: This bundle requires Java to be installed on the target system."
    echo "For a self-contained app with bundled JRE, use: $0 --use-jpackage"
fi

echo ""
echo "To sign and notarize, run: ./sign-and-notarize.sh"
