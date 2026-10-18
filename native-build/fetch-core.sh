#!/usr/bin/env bash
# Resolves the elektron-net ref for the current build type and checks out that exact commit.
set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=core-source.env
source "$SCRIPT_DIR/core-source.env"
CACHE_DIR=${CACHE_DIR:-$SCRIPT_DIR/build-cache}

log() { echo "fetch-core: $*" >&2; }
die() { log "error: $*"; exit 1; }

# stdin: `git ls-remote --tags` output. stdout: "<tag> <commit sha>" for final version tags, ascending.
release_tags() {
    awk '
        {
            ref = $2
            sub(/^refs\/tags\//, "", ref)
            if (ref ~ /\^\{\}$/) { sub(/\^\{\}$/, "", ref); sha[ref] = $1; peeled[ref] = 1 }
            else if (!(ref in peeled)) { sha[ref] = $1 }
        }
        END {
            for (ref in sha)
                if (ref ~ /^v?[0-9]+\.[0-9]+\.[0-9]+$/) print ref, sha[ref]
        }
    ' | sort -V -k1,1
}

remote_release_tags() {
    git ls-remote --tags "$CORE_REPO_URL" | release_tags
}

resolve_ref() {
    local ref=$1 out sha
    if [[ $ref =~ ^[0-9a-f]{40}$ ]]; then
        echo "$ref"
        return
    fi
    out=$(git ls-remote "$CORE_REPO_URL" "refs/tags/$ref^{}" "refs/tags/$ref" "refs/heads/$ref")
    sha=$(awk '$2 ~ /\^\{\}$/ {print $1; exit}' <<<"$out")
    [[ -n $sha ]] || sha=$(awk 'NR == 1 {print $1}' <<<"$out")
    [[ -n $sha ]] || die "ref '$ref' not found in $CORE_REPO_URL"
    echo "$sha"
}

select_ref() {
    local tags tag sha
    if [[ $BUILD_TYPE == release ]]; then
        tags=$(remote_release_tags)
        [[ -n $tags ]] || die "release build requested but $CORE_REPO_URL has no final release tag"
        if [[ -n $CORE_REF ]]; then
            read -r tag sha < <(awk -v r="$CORE_REF" '$1 == r' <<<"$tags") || true
            [[ -n ${tag:-} ]] || die "CORE_REF '$CORE_REF' is not a release tag, release builds only accept release tags"
        else
            read -r tag sha < <(tail -n 1 <<<"$tags")
        fi
        CORE_TAG=$tag
        CORE_REF_NAME=$tag
        CORE_SHA=$sha
        return
    fi
    CORE_TAG=
    CORE_REF_NAME=${CORE_REF:-$CORE_DEFAULT_BRANCH}
    CORE_SHA=$(resolve_ref "$CORE_REF_NAME")
}

checkout() {
    local src=$CACHE_DIR/elektron-net
    mkdir -p "$CACHE_DIR"
    if [[ ! -d $src/.git ]]; then
        rm -rf "$src"
        git init -q "$src"
        git -C "$src" remote add origin "$CORE_REPO_URL"
    fi
    git -C "$src" remote set-url origin "$CORE_REPO_URL"
    git -C "$src" fetch -q --depth 1 origin "$CORE_SHA"
    CORE_SHA=$(git -C "$src" rev-parse "FETCH_HEAD^{commit}")
    git -C "$src" checkout -q --force --detach "$CORE_SHA"
    git -C "$src" clean -q -ffdx
    [[ $(git -C "$src" rev-parse HEAD) == "$CORE_SHA" ]] || die "checked out commit does not match $CORE_SHA"
    CORE_COMMIT_DATE=$(git -C "$src" show -s --format=%cI HEAD)
}

write_manifest() {
    cat > "$CACHE_DIR/core-source.resolved.env" <<MANIFEST
CORE_REPO_URL=$CORE_REPO_URL
BUILD_TYPE=$BUILD_TYPE
CORE_REF_NAME=$CORE_REF_NAME
CORE_TAG=$CORE_TAG
CORE_SHA=$CORE_SHA
CORE_COMMIT_DATE=$CORE_COMMIT_DATE
MANIFEST
}

main() {
    case $BUILD_TYPE in
        dev | nightly | release) ;;
        *) die "BUILD_TYPE must be dev, nightly or release, got '$BUILD_TYPE'" ;;
    esac
    select_ref
    log "build type $BUILD_TYPE, ref $CORE_REF_NAME, commit $CORE_SHA"
    checkout
    write_manifest
    log "source ready in $CACHE_DIR/elektron-net"
}

if [[ ${BASH_SOURCE[0]} == "$0" ]]; then
    main "$@"
fi
