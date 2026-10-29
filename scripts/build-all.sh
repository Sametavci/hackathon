#!/usr/bin/env bash
# Builds the frontend, bundles it into Quarkus and packages one runnable app (backend/target/quarkus-app).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SPA_DIR="$ROOT/backend/src/main/resources/META-INF/resources"

# Same output on every Mac (Intel or Apple Silicon): the result is a plain JVM app for the x86_64 server.
java_major() { "$1" -version 2>&1 | head -1 | sed -E 's/.*version "([0-9]+).*/\1/'; }
if [ "$(java_major java)" -lt 21 ] 2>/dev/null || ! command -v java >/dev/null; then
  if JAVA_HOME="$("${JAVA_HOME_TOOL:-/usr/libexec/java_home}" -v 21+ 2>/dev/null)"; then
    export JAVA_HOME PATH="$JAVA_HOME/bin:$PATH"
  else
    echo "JDK 21+ required (found: $(java -version 2>&1 | head -1)). Install: brew install --cask temurin@21"; exit 1
  fi
fi
NODE_MAJOR="$(node -p 'process.versions.node.split(".")[0]')"
[ "$NODE_MAJOR" -ge 20 ] || { echo "Node 20+ required (found $(node -v))"; exit 1; }
echo "==> Using $(java -version 2>&1 | head -1), node $(node -v), $(uname -m)"

echo "==> Building frontend"
(cd "$ROOT/frontend" && npm ci --silent && npm run build)

echo "==> Copying frontend into Quarkus static resources"
rm -rf "$SPA_DIR" && mkdir -p "$SPA_DIR"
cp -R "$ROOT/frontend/dist/." "$SPA_DIR/"

echo "==> Packaging backend"
# clean: stale classes of deleted files must not end up in the deployed app
(cd "$ROOT/backend" && ./mvnw -B -q clean package -DskipTests)

echo "==> Done: java -jar backend/target/quarkus-app/quarkus-run.jar  (serves UI + API on :8080)"
