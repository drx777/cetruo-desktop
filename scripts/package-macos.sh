#!/bin/bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

if ! command -v gradle >/dev/null 2>&1; then
  echo "gradle is required (configure IntelliJ to use the local Gradle installation)." >&2
  exit 1
fi

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
  --main-class com.example.cardforge.MainKt \
  --icon packaging/macos/CardForge.icns \
  --dest build/macos

echo "Created: $ROOT/build/macos/Cetruo Desktop.app"
