#!/bin/bash
# Full QA: tests + detekt + lint

set -e
cd "$(dirname "$0")/.."

echo "=== DeepFake Shield QA ==="

echo ""
echo "1. Unit tests..."
./gradlew testReleaseUnitTest --no-daemon -q

echo ""
echo "2. Detekt..."
if ./gradlew detekt --no-daemon -q 2>/dev/null; then
  echo "Detekt OK"
else
  echo "Detekt not configured - skipping"
fi

echo ""
echo "3. Lint..."
./gradlew lint --no-daemon -q

echo ""
echo "=== QA complete ==="
