# Galaxy Simulator Makefile
#
# Targets:
#   make          - Build JAR file (default)
#   make jar      - Build JAR file
#   make app      - Build macOS .app bundle (unsigned)
#   make sign     - Sign and notarize for distribution
#   make run      - Build and run the JAR
#   make run-app  - Build and run the .app bundle
#   make clean    - Remove build artifacts
#   make distclean - Remove all generated files including dist
#
# Requirements:
#   - Java 25+ (for jpackage and language features)
#   - For signing: Apple Developer certificate and notarization credentials

SHELL := /bin/bash

# Minimum Java version required
MIN_JAVA_VERSION := 25

# Directories
PROJECT_DIR := $(shell pwd)
BUILD_DIR := $(PROJECT_DIR)/build
DIST_DIR := $(PROJECT_DIR)/dist
SCRIPTS_DIR := $(PROJECT_DIR)/scripts

# Java settings
JAVAC := javac
JAVA := java
JAR := jar
JPACKAGE := jpackage
JAVAC_FLAGS := -Xlint:-deprecation -Xlint:-removal

# Output files
JAR_FILE := $(DIST_DIR)/GalaxySim.jar
APP_BUNDLE := $(DIST_DIR)/GalaxySimulator.app
MAIN_CLASS := astro.galaxy.Galaxy

# Source files
SOURCES := \
	astro/galaxy/Galaxy.java \
	astro/galaxy/GalaxyCanvas.java \
	astro/galaxy/Body.java \
	astro/galaxy/Data.java \
	myutil/AppletFrame.java \
	myutil/GL.java \
	myutil/SL.java \
	myutil/Format.java

# App bundle settings
APP_NAME := GalaxySimulator
DISPLAY_NAME := Galaxy Simulator
BUNDLE_ID := edu.princeton.vanderbei.galaxy-sim
VERSION := 1.0.0

# Check Java version
JAVA_VERSION_RAW := $(shell $(JAVAC) -version 2>&1 | head -1 | sed 's/javac //' | cut -d. -f1)
JAVA_VERSION := $(shell echo $(JAVA_VERSION_RAW) | grep -o '^[0-9]*')

.PHONY: check-java
check-java:
	@if [ -z "$(JAVA_VERSION)" ]; then \
		echo ""; \
		echo "ERROR: Java compiler (javac) not found."; \
		echo ""; \
		echo "Please install Java $(MIN_JAVA_VERSION) or later:"; \
		echo ""; \
		echo "  macOS (Homebrew):"; \
		echo "    brew install openjdk"; \
		echo ""; \
		echo "  Or download from:"; \
		echo "    https://adoptium.net/"; \
		echo ""; \
		exit 1; \
	elif [ "$(JAVA_VERSION)" -lt "$(MIN_JAVA_VERSION)" ]; then \
		echo ""; \
		echo "ERROR: Java $(MIN_JAVA_VERSION)+ required, but found Java $(JAVA_VERSION)."; \
		echo ""; \
		echo "Please upgrade your Java installation:"; \
		echo ""; \
		echo "  macOS (Homebrew):"; \
		echo "    brew upgrade openjdk"; \
		echo "    # or"; \
		echo "    brew install openjdk"; \
		echo ""; \
		echo "  Or download from:"; \
		echo "    https://adoptium.net/"; \
		echo ""; \
		echo "  After installing, ensure it's in your PATH:"; \
		echo "    export JAVA_HOME=\$$(brew --prefix openjdk)"; \
		echo "    export PATH=\$$JAVA_HOME/bin:\$$PATH"; \
		echo ""; \
		exit 1; \
	fi

# Default target
.PHONY: all
all: jar

# Compile Java sources and create JAR
.PHONY: jar
jar: check-java $(JAR_FILE)

$(JAR_FILE): $(SOURCES)
	@echo "=== Compiling Java sources ==="
	@mkdir -p $(BUILD_DIR) $(DIST_DIR)
	$(JAVAC) -d $(BUILD_DIR) -sourcepath . $(JAVAC_FLAGS) $(SOURCES)
	@echo "=== Creating JAR file ==="
	cd $(BUILD_DIR) && $(JAR) cfe $(JAR_FILE) $(MAIN_CLASS) astro/ myutil/
	@echo "Build complete: $(JAR_FILE)"

# Build macOS .app bundle (unsigned)
.PHONY: app
app: $(APP_BUNDLE)

$(APP_BUNDLE): $(JAR_FILE)
	@echo "=== Creating macOS App Bundle ==="
	@rm -rf $(APP_BUNDLE)
	$(JPACKAGE) \
		--type app-image \
		--name "$(APP_NAME)" \
		--input $(DIST_DIR) \
		--main-jar GalaxySim.jar \
		--main-class $(MAIN_CLASS) \
		--dest $(DIST_DIR) \
		--app-version $(VERSION) \
		--vendor "Vanderbei" \
		--mac-package-identifier $(BUNDLE_ID) \
		--mac-package-name "$(DISPLAY_NAME)" \
		--java-options "-Xmx512m"
	@echo "App bundle created: $(APP_BUNDLE)"

# Sign and notarize for distribution
.PHONY: sign
sign: app
	@echo "=== Signing and Notarizing ==="
	@if [ -x "$(SCRIPTS_DIR)/sign-and-notarize.sh" ]; then \
		$(SCRIPTS_DIR)/sign-and-notarize.sh; \
	else \
		echo "Error: sign-and-notarize.sh not found or not executable"; \
		exit 1; \
	fi

# Run the JAR
.PHONY: run
run: jar
	@echo "=== Running Galaxy Simulator ==="
	$(JAVA) -jar $(JAR_FILE)

# Run the app bundle
.PHONY: run-app
run-app: app
	@echo "=== Running Galaxy Simulator App ==="
	open $(APP_BUNDLE)

# Clean build artifacts
.PHONY: clean
clean:
	@echo "=== Cleaning build artifacts ==="
	rm -rf $(BUILD_DIR)
	rm -f $(JAR_FILE)
	@echo "Clean complete"

# Clean everything including dist
.PHONY: distclean
distclean: clean
	@echo "=== Removing all generated files ==="
	rm -rf $(DIST_DIR)
	@echo "Distclean complete"

# Help
.PHONY: help
help:
	@echo "Galaxy Simulator Makefile"
	@echo ""
	@echo "Usage: make [target]"
	@echo ""
	@echo "Targets:"
	@echo "  all        - Build JAR file (default)"
	@echo "  jar        - Build JAR file"
	@echo "  app        - Build macOS .app bundle (unsigned)"
	@echo "  sign       - Sign and notarize for distribution"
	@echo "  run        - Build and run the JAR"
	@echo "  run-app    - Build and run the .app bundle"
	@echo "  clean      - Remove build artifacts"
	@echo "  distclean  - Remove all generated files including dist"
	@echo "  help       - Show this help message"
