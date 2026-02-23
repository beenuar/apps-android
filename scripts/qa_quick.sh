#!/bin/bash
# Quick QA: unit tests only

set -e
cd "$(dirname "$0")/.."

echo "=== Quick QA (unit tests) ==="
./gradlew testReleaseUnitTest --no-daemon -q
echo "=== Done ==="
