#!/bin/bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APP="$ROOT/build/macos/Cetruo Desktop.app"
if [ ! -d "$APP" ]; then
  echo "Native app bundle not found. Run scripts/package-macos.sh first." >&2
  exit 1
fi
open "$APP"
