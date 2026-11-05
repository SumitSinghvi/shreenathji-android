#!/usr/bin/env sh
# Copy the challan reference samples from the dashboard repo (the master copy).
# Usage: scripts/sync-fixtures.sh [path-to-dashboard]   (default: ../dashboard)
set -e
cd "$(dirname "$0")/.."
SRC="${1:-../dashboard}/challan-fixtures"
DEST="app/src/test/resources/challan-fixtures"
[ -d "$SRC" ] || { echo "Not found: $SRC" >&2; exit 1; }
rm -rf "$DEST"
mkdir -p "$DEST"
cp "$SRC"/*.json "$SRC"/VERSION "$SRC"/README.md "$DEST"/
echo "Synced challan fixtures v$(cat "$DEST/VERSION") from $SRC"
