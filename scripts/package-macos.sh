#!/bin/bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

rm -rf build/macos
mkdir -p build/macos/input

./gradlew clean jar installDist
cp build/libs/*.jar build/macos/input/

# Copy runtime dependencies produced by installDist so the app image keeps the same classpath.
cp -R build/install/cetruo-desktop/lib build/macos/input/

jpackage \
  --type app-image \
  --name "Cetruo Desktop" \
  --input build/macos/input \
  --main-jar "$(basename build/libs/*.jar)" \
  --main-class de.cetruo.desktop.MainKt \
  --icon packaging/macos/Cetruo.icns \
  --dest build/macos

echo "Created: $ROOT/build/macos/Cetruo Desktop.app"
