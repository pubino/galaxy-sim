# Galaxy Simulator

Part of [Vanderbei's Java Simulations](https://github.com/pubino/vanderbei-apps) collection.

A Java-based gravitational N-body simulator by Prof. Robert Vanderbei, Princeton University. This application visualizes orbital mechanics and celestial body interactions, demonstrating various periodic solutions to the N-body problem, including Lagrange points, figure-eight orbits, and other choreographic solutions.

## Running the Application

### Prerequisites

- Java 11 or later (tested with Java 25)

### Quick Start

From the `galaxy-sim` directory:

```bash
# Build and run
make run

# Or build the macOS app and double-click it
make app
open dist/GalaxySimulator.app
```

### Usage

1. **Select a simulation** from the "Instance" dropdown menu
2. Click **Start** to begin the animation
3. Use **Pause/Play** to control the simulation
4. Click **Show Trails** to see orbital paths
5. Adjust parameters:
   - **dt**: Time step for numerical integration
   - **warp**: Speed multiplier (frames computed per display update)
   - **FrameRotationRate**: Rotate the reference frame
   - **n**: Number of bodies (for configurable simulations)

### Available Simulations

| Simulation | Description |
|------------|-------------|
| L1-L5 | Lagrange point demonstrations with mass ratios |
| LagrangeN | N bodies in circular Lagrangian configuration |
| FigureEight3 | Famous 3-body figure-eight choreography |
| FigureEight4/5/21 | Figure-eight solutions with more bodies |
| Trefoil4/5 | Trefoil knot orbital patterns |
| Ducati3 | Three-body "motorcycle" orbit variants |
| Hill15/Hill3_* | Hill's problem configurations |
| Saturn/Saturn2 | Saturn-like ring systems |
| Rosette4 | Four-body rosette pattern |
| Braid4 | Braided orbital choreography |
| BorderCollie4 | Complex 4-body periodic solution |
| DoubleDouble* | Binary-binary systems |

### Numerical Integrators

- **Hermite** (default): 4th-order predictor-corrector, highly accurate
- **Midpoint (Leapfrog)**: Symplectic, conserves energy well
- **RungeKutta4**: Classic 4th-order method
- **Euler**: Simple first-order (for educational purposes)
- **TrapNewt**: Trapezoidal with Newton correction

## Development

### Project Structure

```
galaxy-sim/
├── astro/galaxy/
│   ├── Galaxy.java       # Main application (JApplet-based GUI)
│   ├── GalaxyCanvas.java # Animation canvas and physics engine
│   ├── Data.java         # Initial conditions for all simulations
│   └── Body.java         # Celestial body class
├── myutil/
│   ├── AppletFrame.java  # Frame wrapper for running applets standalone
│   ├── GL.java           # Graphics library for 2D drawing
│   ├── SL.java           # String/number parsing utilities
│   └── Format.java       # Number formatting utilities
├── scripts/
│   ├── build.sh          # Build JAR file
│   ├── create-app-bundle.sh  # Create macOS .app bundle
│   ├── setup-notarization.sh # Setup Apple credentials
│   └── sign-and-notarize.sh  # Sign and notarize for distribution
├── Makefile              # Build automation
├── README.md
└── .gitignore
```

### Building with Make

The easiest way to build is using the provided Makefile:

```bash
# Build JAR file
make

# Build macOS app bundle (with bundled JRE)
make app

# Build and run
make run        # Run JAR
make run-app    # Run macOS app

# Clean build artifacts
make clean      # Remove build directory
make distclean  # Remove build and dist directories

# Sign and notarize for distribution (requires Apple Developer credentials)
make sign
```

### Make Targets

| Target | Description |
|--------|-------------|
| `make` or `make jar` | Compile and create JAR file |
| `make app` | Build macOS .app bundle (unsigned, with bundled JRE) |
| `make sign` | Sign and notarize for distribution |
| `make run` | Build and run the JAR |
| `make run-app` | Build and run the .app bundle |
| `make clean` | Remove build artifacts |
| `make distclean` | Remove all generated files including dist |
| `make help` | Show help message |

### Compiling Manually

If you prefer not to use Make:

```bash
# Compile all files (from galaxy-sim directory)
javac -d build -sourcepath . astro/galaxy/Galaxy.java

# Run
java -cp build astro.galaxy.Galaxy
```

### Key Classes

- **Galaxy.java**: Main entry point, builds the GUI with controls
- **GalaxyCanvas.java**: Handles physics simulation using various numerical integrators (Hermite, Leapfrog, Runge-Kutta, etc.)
- **Data.java**: Contains initial positions, velocities, and masses for all choreographic solutions
- **GL.java**: Simple OpenGL-style 2D graphics abstraction

### Adding New Simulations

1. Add initial conditions to `Data.java` in the `init()` method
2. Add the simulation name to the `SELECTION` combo box in `Galaxy.java`
3. Add parameter defaults in `itemStateChanged()` in `Galaxy.java`

## Packaging as a macOS Application

The `scripts/` directory contains everything needed to build, sign, and notarize the app for distribution.

### Quick Start

```bash
cd scripts

# 1. Build the JAR
./build.sh

# 2. Create .app bundle
./create-app-bundle.sh              # Lightweight (requires Java on target)
./create-app-bundle.sh --use-jpackage  # Self-contained (bundles JRE)

# 3. Sign and notarize (for distribution)
./setup-notarization.sh             # One-time credential setup
./sign-and-notarize.sh              # Sign, notarize, create DMG
```

### Scripts Overview

| Script | Purpose |
|--------|---------|
| `build.sh` | Compiles sources and creates `GalaxySim.jar` |
| `create-app-bundle.sh` | Creates macOS `.app` bundle |
| `setup-notarization.sh` | Interactive setup for Apple credentials |
| `sign-and-notarize.sh` | Signs app, submits for notarization, creates DMG |

### Signing & Notarization Prerequisites

To distribute your app without Gatekeeper warnings, you need:

1. **Apple Developer Program membership** ($99/year)
   - Enroll at: https://developer.apple.com/programs/

2. **Developer ID Application certificate**
   - Create at: developer.apple.com/account > Certificates
   - Download and install in Keychain Access

3. **App-specific password** (for notarization)
   - Create at: appleid.apple.com > Sign-In and Security > App-Specific Passwords

4. **Team ID**
   - Find at: developer.apple.com/account > Membership

### First-Time Setup

1. Install your Developer ID certificate in Keychain Access

2. Run the setup script to store your credentials:
   ```bash
   ./scripts/setup-notarization.sh
   ```

3. Configure the signing identity in `sign-and-notarize.sh`:
   ```bash
   DEVELOPER_ID_APP="Developer ID Application: Your Name (TEAM_ID)"
   ```

### Building for Distribution

```bash
# Build everything and create a signed, notarized DMG
./scripts/build.sh
./scripts/create-app-bundle.sh --use-jpackage
./scripts/sign-and-notarize.sh
```

The output `dist/GalaxySim.dmg` is ready for distribution. Users can:
1. Download the DMG
2. Open it and drag the app to Applications
3. Run the app without Gatekeeper warnings

### Manual Signing Commands

If you prefer manual control:

```bash
# Sign the app
codesign --force --deep --options runtime --timestamp \
    --sign "Developer ID Application: Your Name (TEAM_ID)" \
    --entitlements entitlements.plist \
    "Galaxy Simulator.app"

# Create ZIP and submit for notarization
ditto -c -k --keepParent "Galaxy Simulator.app" GalaxySim.zip
xcrun notarytool submit GalaxySim.zip \
    --keychain-profile "GalaxySim-notarize" \
    --wait

# Staple the ticket
xcrun stapler staple "Galaxy Simulator.app"
```

### Troubleshooting

**"Developer ID Application certificate not found"**
- Open Keychain Access and verify your certificate is installed
- Ensure it's not expired
- The certificate name must match what's in `sign-and-notarize.sh`

**"Notarization failed"**
- Check the log: `xcrun notarytool log <submission-id> --keychain-profile GalaxySim-notarize`
- Common issues: unsigned nested binaries, hardened runtime not enabled

**"App is damaged and can't be opened"**
- The app wasn't properly signed or notarization ticket wasn't stapled
- Re-run `sign-and-notarize.sh`

## History

This simulator was originally written as a Java Applet for web browsers. It has been updated to run as a standalone desktop application. The physics engine implements several numerical integration methods for solving the gravitational N-body problem, with initial conditions derived from research into periodic solutions and choreographic orbits.

## References

- Lagrange Points: https://en.wikipedia.org/wiki/Lagrange_point
- Three-body problem: https://en.wikipedia.org/wiki/Three-body_problem
- Choreographic solutions: Moore (1993), Chenciner & Montgomery (2000)
