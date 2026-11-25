#!/usr/bin/env bash
# tools/pre-commit-check.sh - Fast regression check before committing
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

if [[ "${SKIP_TESTS:-0}" == "1" ]]; then
    echo ">> [PRE-COMMIT] SKIP_TESTS=1 set - skipping verification."
    exit 0
fi

echo "=========================================================="
echo ">> [PRE-COMMIT] Running local verification gate..."
echo "=========================================================="

# 1. Fast static and design checks
echo ">> [1/3] Static checks & design integrity..."
python3 -I design/check-contrast.py
python3 -I tools/check-locales.py .
python3 -I tools/check-rng.py
python3 -I tools/version.py --selftest

# 2. Native fetch script tests
echo ">> [2/3] Native build helper tests..."
bash native-build/tests/test-fetch-core.sh
bash native-build/tests/test-fetch-helpers.sh

# 3. Fast Gradle Unit Tests for both flavors
echo ">> [3/3] Running unit tests for Mainnet & Testnet flavors..."
export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk}"
if [[ ! -d "$JAVA_HOME" ]]; then
    if command -v java >/dev/null 2>&1; then
        echo "   Using system java: $(java -version 2>&1 | head -1)"
    fi
fi

./gradlew testMainnetDebugUnitTest testTnetDebugUnitTest

echo "=========================================================="
echo ">> [PRE-COMMIT] All regression tests passed successfully!"
echo "=========================================================="
