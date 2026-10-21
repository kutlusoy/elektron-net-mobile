#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=../fetch-helpers.sh
source "$SCRIPT_DIR/../fetch-helpers.sh"

fail=0
check() {
    if [[ $2 != "$3" ]]; then
        echo "FAIL $1: expected '$3', got '$2'" >&2
        fail=1
    fi
}

tor_fixture() {
    cat <<'LS'
aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa	refs/tags/tor-0.4.7.13
bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb	refs/tags/tor-0.4.8.9
cccccccccccccccccccccccccccccccccccccccc	refs/tags/tor-0.4.8.9^{}
dddddddddddddddddddddddddddddddddddddddd	refs/tags/tor-0.4.8.10-rc
eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee	refs/tags/tor-0.4.9.1-alpha
ffffffffffffffffffffffffffffffffffffffff	refs/tags/tor-0.4.8.10-dev
1111111111111111111111111111111111111111	refs/tags/tor-0.4.8.2
LS
}

i2pd_fixture() {
    cat <<'LS'
2222222222222222222222222222222222222222	refs/tags/2.9.0
3333333333333333333333333333333333333333	refs/tags/2.61.0
4444444444444444444444444444444444444444	refs/tags/3.0.0
5555555555555555555555555555555555555555	refs/tags/v0.3.0-1stbinrelease
LS
}

tags=$(tor_fixture | final_tags "$TOR_TAG_RE")
check "tor highest is last" "$(tail -n 1 <<<"$tags")" "0.4.8.9 tor-0.4.8.9 cccccccccccccccccccccccccccccccccccccccc"
check "tor version sort" "$(head -n 1 <<<"$tags")" "0.4.7.13 tor-0.4.7.13 aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
check "tor pre-releases excluded" "$(grep -cE 'rc|alpha|dev' <<<"$tags" || true)" "0"
check "tor tag count" "$(wc -l <<<"$tags" | tr -d ' ')" "3"

tags=$(i2pd_fixture | final_tags "$I2PD_TAG_RE")
check "i2pd highest is last" "$(tail -n 1 <<<"$tags")" "3.0.0 3.0.0 4444444444444444444444444444444444444444"
check "i2pd numeric sort" "$(head -n 1 <<<"$tags")" "2.9.0 2.9.0 2222222222222222222222222222222222222222"
check "i2pd binary release tag excluded" "$(grep -c 1stbin <<<"$tags" || true)" "0"
check "empty input" "$(final_tags "$TOR_TAG_RE" </dev/null | wc -l | tr -d ' ')" "0"

# newest_tag takes the highest tag over several repositories, so a stale mirror cannot pin an old release
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
make_repo() {
    local dir=$work/$1
    shift
    git init -q "$dir"
    git -C "$dir" -c user.name=t -c user.email=t@t commit -q --allow-empty -m init
    for tag in "$@"; do git -C "$dir" -c user.name=t -c user.email=t@t tag -a -m "$tag" "$tag"; done
}
make_repo stale tor-0.4.7.12 tor-0.4.7.13
make_repo fresh tor-0.4.7.13 tor-0.4.8.9
best=$(newest_tag "$TOR_TAG_RE" "$work/stale" "$work/fresh" "$work/missing")
read -r version tag sha url <<<"$best"
check "newest across repositories" "$tag" "tor-0.4.8.9"
check "newest comes from the repository that has it" "$url" "$work/fresh"
check "annotated tag is peeled to the commit" "$sha" "$(git -C "$work/fresh" rev-parse 'tor-0.4.8.9^{commit}')"

exit $fail
