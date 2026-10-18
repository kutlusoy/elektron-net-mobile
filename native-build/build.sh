#!/usr/bin/env bash
# Usage: build.sh [arm64-v8a|x86_64|all]
# Fetches the current elektron-net source (see fetch-core.sh) and builds libelektrond.so per ABI.
set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=core-source.env
source "$SCRIPT_DIR/core-source.env"
CACHE_DIR=${CACHE_DIR:-$SCRIPT_DIR/build-cache}
OUT_DIR=${OUT_DIR:-$SCRIPT_DIR/out}
JOBS=${JOBS:-$(nproc)}
IMAGE=elektron-mobile-ndk:$NDK_VERSION

die() { echo "build: error: $*" >&2; exit 1; }

case ${1:-all} in
    all) ABIS=(arm64-v8a x86_64) ;;
    arm64-v8a | x86_64) ABIS=("$1") ;;
    *) die "usage: build.sh [arm64-v8a|x86_64|all]" ;;
esac

command -v docker >/dev/null || die "docker is required"

hash_files() {
    find "$@" -type f 2>/dev/null | LC_ALL=C sort | xargs -r cat | sha256sum | cut -d' ' -f1
}

bash "$SCRIPT_DIR/fetch-core.sh"
# shellcheck source=/dev/null
source "$CACHE_DIR/core-source.resolved.env"

mkdir -p "$SCRIPT_DIR/patches"
PATCH_SET_SHA256=$(hash_files "$SCRIPT_DIR/patches")
BUILDER_SHA256=$(hash_files "$SCRIPT_DIR/docker")

docker build -q -t "$IMAGE" \
    --build-arg NDK_VERSION="$NDK_VERSION" --build-arg NDK_SHA256="$NDK_SHA256" \
    -f "$SCRIPT_DIR/docker/Dockerfile.ndk" "$SCRIPT_DIR/docker" >/dev/null
BUILDER_IMAGE_ID=$(docker image inspect --format '{{.Id}}' "$IMAGE")

mkdir -p "$CACHE_DIR/depends/sources" "$CACHE_DIR/depends/built"

for abi in "${ABIS[@]}"; do
    out=$OUT_DIR/$abi
    stamp=$(printf '%s\n' "$CORE_SHA" "$PATCH_SET_SHA256" "$BUILDER_SHA256" "$NDK_VERSION" "$ANDROID_API" "$abi" | sha256sum | cut -d' ' -f1)

    if [[ -f $out/libelektrond.so && -f $out/manifest.env ]] && grep -qx "STAMP=$stamp" "$out/manifest.env"; then
        echo "build: $abi is up to date for ${CORE_SHA:0:12}"
    else
        echo "build: $abi from ${CORE_SHA:0:12} ($CORE_REF_NAME)"
        rm -rf "$out"
        mkdir -p "$out" "$CACHE_DIR/build-$abi"
        docker run --rm --user "$(id -u):$(id -g)" -e HOME=/tmp \
            -e ABI="$abi" -e ANDROID_API="$ANDROID_API" -e JOBS="$JOBS" \
            -v "$CACHE_DIR/elektron-net:/src" \
            -v "$SCRIPT_DIR/patches:/patches:ro" \
            -v "$CACHE_DIR/depends:/cache" \
            -v "$CACHE_DIR/build-$abi:/build" \
            -v "$out:/out" \
            "$IMAGE" /usr/local/bin/build-in-container.sh

        cat > "$out/manifest.env" <<MANIFEST
STAMP=$stamp
ABI=$abi
CORE_REPO_URL=$CORE_REPO_URL
BUILD_TYPE=$BUILD_TYPE
CORE_REF_NAME=$CORE_REF_NAME
CORE_TAG=$CORE_TAG
CORE_SHA=$CORE_SHA
CORE_COMMIT_DATE=$CORE_COMMIT_DATE
PATCH_SET_SHA256=$PATCH_SET_SHA256
NDK_VERSION=$NDK_VERSION
ANDROID_API=$ANDROID_API
BUILDER_IMAGE_ID=$BUILDER_IMAGE_ID
LIB_SHA256=$(cat "$out/libelektrond.so.sha256")
MANIFEST
    fi

    OUT_DIR=$OUT_DIR bash "$SCRIPT_DIR/install-core.sh" "$abi"
done
