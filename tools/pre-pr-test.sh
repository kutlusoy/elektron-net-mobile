#!/usr/bin/env bash
# tools/pre-pr-test.sh - Full CI gate verification before submitting or updating a PR
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

echo "=========================================================="
echo ">> [PRE-PR] Running full CI parity test gate..."
echo "=========================================================="

export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk}"

# Step 1: Checks job
echo ">> [1/4] Running checks job (static, contrast, locales, version, native)..."
python3 -I design/check-contrast.py
python3 -I design/gen-theme.py --out "${TMPDIR:-/tmp}/elektron-theme"
python3 -I tools/check-locales.py .
python3 -I tools/check-rng.py
python3 -I tools/version.py --selftest
bash native-build/tests/test-fetch-core.sh
bash native-build/tests/test-fetch-helpers.sh

# Step 2: Peer country DB
if [[ -f tools/fetch-geoip.sh ]]; then
    echo ">> [2/4] Peer country database..."
    bash tools/fetch-geoip.sh
fi

# Step 3: Build both flavors
echo ">> [3/4] Building APKs (:app:assembleMainnetDebug :app:assembleTnetDebug)..."
./gradlew :app:assembleMainnetDebug :app:assembleTnetDebug

# Step 4: Unit tests & lint
echo ">> [4/4] Running unit tests and lint for both flavors..."
if ! ./gradlew :app:testMainnetDebugUnitTest :app:testTnetDebugUnitTest :app:lintMainnetDebug :app:lintTnetDebug; then
    echo "=========================================================="
    echo ">> [PRE-PR] FAILURE: Lint or Unit Tests failed!"
    echo ">> Detailed lint reports (if any):"
    grep -h -A3 "Error:" app/build/reports/lint-results-*.txt app/build/intermediates/lint_intermediate_text_report/*/*/lint-results-*.txt 2>/dev/null | head -100 || true
    echo "=========================================================="
    exit 1
fi

echo "=========================================================="
echo ">> [PRE-PR] SUCCESS: All CI checks, builds, tests and lint passed!"
echo ">> Your branch is ready for PR."
echo "=========================================================="
