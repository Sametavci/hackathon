#!/usr/bin/env bash
# Build the frontend and backend, then serve both from Quarkus.
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$PROJECT_ROOT"

for required_command in java node npm; do
  if ! command -v "$required_command" >/dev/null 2>&1; then
    printf 'Eksik komut: %s. Java 21 ve Node.js 20+ kurulu olmali.\n' "$required_command" >&2
    exit 1
  fi
done

cd "$PROJECT_ROOT/frontend"
npm ci
VITE_USE_MOCKS=false npm run build

# This directory is reserved for generated SPA assets (git-ignored).
SPA_DEST="$PROJECT_ROOT/backend/src/main/resources/META-INF/resources"
mkdir -p "$SPA_DEST"
find "$SPA_DEST" -mindepth 1 -maxdepth 1 -exec rm -rf {} +
cp -R dist/. "$SPA_DEST/"

cd "$PROJECT_ROOT/backend"
./mvnw -q -DskipTests package
cd "$PROJECT_ROOT"

# The built SPA and API share port 8080, including invite/restore links.
export LUNCH2_PUBLIC_BASE_URL="${LUNCH2_PUBLIC_BASE_URL:-http://localhost:8080}"
printf '\nLunch²: http://localhost:8080\nDurdurmak icin Ctrl+C.\n\n'
exec java -jar "$PROJECT_ROOT/backend/target/quarkus-app/quarkus-run.jar"
