#!/usr/bin/env bash
set -euo pipefail

# Run against an empty demo instance. Reset is always an explicit, separate admin action.
# Usage: LUNCH2_API_BASE_URL=http://localhost:8080 LUNCH2_ADMIN_KEY=... ./scripts/demo.sh
# Requires curl and Python 3. Restore links printed below grant access to fictional demo accounts.
api_base="${LUNCH2_API_BASE_URL:-http://localhost:8080}"
api_base="${api_base%/}"
admin_key="${LUNCH2_ADMIN_KEY:-dev-admin-key}"

for dependency in curl python3; do
    command -v "$dependency" >/dev/null || { printf 'Missing dependency: %s\n' "$dependency" >&2; exit 1; }
done

printf 'Seeding fictional demo data (existing data will not be reset)...\n'
curl --fail --silent --show-error --connect-timeout 5 --max-time 30 \
    -X POST -H "X-Admin-Key: $admin_key" "$api_base/api/admin/seed" |
    python3 -c 'import json,sys
people=json.load(sys.stdin)["participants"]
print(f"Created {len(people)} fictional participants. Keep these access links private:")
for person in people:
    print("{displayName} ({campus}, {category}): {restoreUrl}".format(**person))'

printf '\nRunning matching...\n'
curl --fail --silent --show-error --connect-timeout 5 --max-time 30 \
    -X POST -H "X-Admin-Key: $admin_key" "$api_base/api/admin/match" |
    python3 -c 'import json,sys
result=json.load(sys.stdin)
print(json.dumps(result, indent=2, ensure_ascii=False))
print("\nLive demo: register Lena at Würzburg, second event weekday, 12:15, en; invite Sam.")
print("Run POST /api/admin/match again to meet Aiko and Omar.")'
