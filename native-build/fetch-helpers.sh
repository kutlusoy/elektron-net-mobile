#!/usr/bin/env bash
# Resolves the newest stable Tor and i2pd release tags and checks out those exact commits.
set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=helpers-source.env
source "$SCRIPT_DIR/helpers-source.env"
CACHE_DIR=${CACHE_DIR:-$SCRIPT_DIR/build-cache}

log() { echo "fetch-helpers: $*" >&2; }
die() { log "error: $*"; exit 1; }

# stdin: `git ls-remote --tags` output. $1: extended regex for a final release tag, with the version in group 1.
# stdout: "<version> <tag> <commit sha>" ascending by version.
final_tags() {
    awk -v re="$1" '
        {
            ref = $2
            sub(/^refs\/tags\//, "", ref)
            if (ref ~ /\^\{\}$/) { sub(/\^\{\}$/, "", ref); sha[ref] = $1; peeled[ref] = 1 }
            else if (!(ref in peeled)) { sha[ref] = $1 }
        }
        END {
            for (ref in sha)
                if (match(ref, re)) {
                    version = ref
                    sub(/^tor-/, "", version)
                    sub(/^v/, "", version)
                    print version, ref, sha[ref]
                }
        }
    ' | sort -V -k1,1
}

TOR_TAG_RE='^tor-[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$'
I2PD_TAG_RE='^v?[0-9]+\.[0-9]+\.[0-9]+$'

# $1: tag regex, $2...: repository urls. stdout: "<version> <tag> <sha> <url>" for the highest final tag over all urls.
newest_tag() {
    local re=$1 url line best=
    shift
    for url in "$@"; do
        line=$(git ls-remote --tags "$url" 2>/dev/null | final_tags "$re" | tail -n 1) || line=
        if [[ -z $line ]]; then
            log "no usable release tag from $url"
            continue
        fi
        best=$(printf '%s\n%s %s\n' "$best" "$line" "$url" | sed '/^$/d' | sort -V -k1,1 | tail -n 1)
    done
    echo "$best"
}

# $1: name, $2: pinned ref or empty, $3: tag regex, $4...: urls. Sets <NAME>_TAG, <NAME>_SHA, <NAME>_URL.
resolve() {
    local name=$1 ref=$2 re=$3 line version tag sha url
    shift 3
    if [[ -n $ref ]]; then
        for url in "$@"; do
            line=$(git ls-remote --tags "$url" 2>/dev/null | final_tags "$re" | awk -v r="$ref" '$2 == r {print $0}') || line=
            if [[ -n $line ]]; then
                read -r version tag sha <<<"$line"
                eval "${name}_TAG=$tag ${name}_SHA=$sha ${name}_URL=$url"
                return
            fi
        done
        die "$name ref '$ref' is not a final release tag in any repository"
    fi
    line=$(newest_tag "$re" "$@")
    [[ -n $line ]] || die "no final release tag found for $name"
    read -r version tag sha url <<<"$line"
    eval "${name}_TAG=$tag ${name}_SHA=$sha ${name}_URL=$url"
}

checkout() {
    local name=$1 url=$2 tag=$3 sha=$4 dir=$CACHE_DIR/$5
    mkdir -p "$CACHE_DIR"
    if [[ ! -d $dir/.git ]]; then
        rm -rf "$dir"
        git init -q "$dir"
        git -C "$dir" remote add origin "$url"
    fi
    git -C "$dir" remote set-url origin "$url"
    git -C "$dir" fetch -q --depth 1 origin "refs/tags/$tag"
    [[ $(git -C "$dir" rev-parse "FETCH_HEAD^{commit}") == "$sha" ]] || die "$name tag $tag does not point at $sha"
    git -C "$dir" checkout -q --force --detach "$sha"
    git -C "$dir" clean -q -ffdx
}

main() {
    # shellcheck disable=SC2086
    resolve TOR "$TOR_REF" "$TOR_TAG_RE" $TOR_REPO_URLS
    resolve I2PD "$I2PD_REF" "$I2PD_TAG_RE" "$I2PD_REPO_URL"
    log "tor $TOR_TAG ($TOR_SHA) from $TOR_URL"
    log "i2pd $I2PD_TAG ($I2PD_SHA) from $I2PD_URL"
    checkout tor "$TOR_URL" "$TOR_TAG" "$TOR_SHA" tor
    checkout i2pd "$I2PD_URL" "$I2PD_TAG" "$I2PD_SHA" i2pd
    cat > "$CACHE_DIR/helpers-source.resolved.env" <<MANIFEST
TOR_REPO_URL=$TOR_URL
TOR_TAG=$TOR_TAG
TOR_SHA=$TOR_SHA
I2PD_REPO_URL=$I2PD_URL
I2PD_TAG=$I2PD_TAG
I2PD_SHA=$I2PD_SHA
MANIFEST
    log "sources ready in $CACHE_DIR"
}

if [[ ${BASH_SOURCE[0]} == "$0" ]]; then
    main "$@"
fi
