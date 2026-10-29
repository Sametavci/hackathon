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

PORT=8080

# Stop a Lunch² instance left over from a previous run so the new build can take the port.
stop_old_server() {
  local pids pid cmd
  pids="$(lsof -nP -t -iTCP:"$PORT" -sTCP:LISTEN 2>/dev/null || true)"
  [ -z "$pids" ] && return 0
  for pid in $pids; do
    cmd="$(ps -o command= -p "$pid" 2>/dev/null || true)"
    case "$cmd" in
      *quarkus-run.jar*|*quarkus:dev*|*quarkus-maven-plugin*)
        printf 'Port %s uzerindeki eski Lunch² kapatiliyor (PID %s)\n' "$PORT" "$pid"
        kill "$pid" 2>/dev/null || true
        ;;
      *)
        printf 'Port %s baska bir uygulama tarafindan kullaniliyor (PID %s): %s\n' "$PORT" "$pid" "$cmd" >&2
        exit 1
        ;;
    esac
  done
  for _ in $(seq 1 20); do
    lsof -nP -t -iTCP:"$PORT" -sTCP:LISTEN >/dev/null 2>&1 || return 0
    sleep 0.5
  done
  printf 'Eski sunucu kapanmadi, zorla durduruluyor.\n'
  kill -9 $(lsof -nP -t -iTCP:"$PORT" -sTCP:LISTEN) 2>/dev/null || true
}
stop_old_server

cd "$PROJECT_ROOT/frontend"
npm ci
VITE_USE_MOCKS=false npm run build

# This directory is reserved for generated SPA assets (git-ignored).
SPA_DEST="$PROJECT_ROOT/backend/src/main/resources/META-INF/resources"
mkdir -p "$SPA_DEST"
find "$SPA_DEST" -mindepth 1 -maxdepth 1 -exec rm -rf {} +
cp -R dist/. "$SPA_DEST/"

cd "$PROJECT_ROOT/backend"
# clean: stale classes of deleted files must not end up in the app
./mvnw -q -DskipTests clean package
cd "$PROJECT_ROOT"

# The built SPA and API share port 8080, including invite/restore links.
export LUNCH2_PUBLIC_BASE_URL="${LUNCH2_PUBLIC_BASE_URL:-http://localhost:$PORT}"
printf '\nLunch²: http://localhost:%s\nDurdurmak icin Ctrl+C.\n\n' "$PORT"
exec java -jar "$PROJECT_ROOT/backend/target/quarkus-app/quarkus-run.jar"
