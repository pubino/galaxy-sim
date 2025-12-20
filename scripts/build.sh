#!/bin/bash
#
# build.sh - Compile and package Galaxy Simulator
#
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
BUILD_DIR="$PROJECT_DIR/build"
DIST_DIR="$PROJECT_DIR/dist"

echo "=== Galaxy Simulator Build Script ==="
echo "Project directory: $PROJECT_DIR"

# Clean previous builds
rm -rf "$BUILD_DIR" "$DIST_DIR"
mkdir -p "$BUILD_DIR" "$DIST_DIR"

# Compile Java sources
# Using -sourcepath lets javac automatically find and compile dependencies
echo "Compiling Java sources..."

cd "$PROJECT_DIR"
javac -d "$BUILD_DIR" \
    -sourcepath . \
    -Xlint:-deprecation -Xlint:-removal \
    astro/galaxy/Galaxy.java \
    astro/galaxy/GalaxyCanvas.java \
    astro/galaxy/Body.java \
    astro/galaxy/Data.java \
    myutil/AppletFrame.java \
    myutil/GL.java \
    myutil/SL.java \
    myutil/Format.java

# Create JAR file
echo "Creating JAR file..."
cd "$BUILD_DIR"
jar cfe "$DIST_DIR/GalaxySim.jar" astro.galaxy.Galaxy astro/ myutil/

echo "Build complete!"
echo "JAR file: $DIST_DIR/GalaxySim.jar"
echo ""
echo "To run: java -jar $DIST_DIR/GalaxySim.jar"
