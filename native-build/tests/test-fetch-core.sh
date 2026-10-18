#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=../fetch-core.sh
source "$SCRIPT_DIR/../fetch-core.sh"

fixture() {
    cat <<'LS'
aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa	refs/tags/v4.0.9
bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb	refs/tags/v4.0.10
cccccccccccccccccccccccccccccccccccccccc	refs/tags/v4.0.10^{}
dddddddddddddddddddddddddddddddddddddddd	refs/tags/v4.1.0-rc1
eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee	refs/tags/nightly-20260101
ffffffffffffffffffffffffffffffffffffffff	refs/tags/v4.0.2
LS
}

fail=0
check() {
    if [[ $2 != "$3" ]]; then
        echo "FAIL $1: expected '$3', got '$2'" >&2
        fail=1
    fi
}

tags=$(fixture | release_tags)
check "highest is last" "$(tail -n 1 <<<"$tags")" "v4.0.10 cccccccccccccccccccccccccccccccccccccccc"
check "version sort" "$(head -n 1 <<<"$tags")" "v4.0.2 ffffffffffffffffffffffffffffffffffffffff"
check "pre-release excluded" "$(grep -c rc1 <<<"$tags" || true)" "0"
check "non-version tag excluded" "$(grep -c nightly <<<"$tags" || true)" "0"
check "tag count" "$(wc -l <<<"$tags" | tr -d ' ')" "3"
check "empty input" "$(release_tags </dev/null | wc -l | tr -d ' ')" "0"

exit $fail
