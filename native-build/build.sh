#!/usr/bin/env bash
# Usage: build.sh [arm64-v8a|x86_64|host|all]
# Fetches the current elektron-net source (see fetch-core.sh) and builds libelektrond.so per ABI.
# "host" builds a native elektrond that runs the contract check. Android builds require it unless CONTRACT_CHECK=0.
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
    all) ABIS=(host arm64-v8a x86_64) ;;
    arm64-v8a | x86_64 | host) ABIS=("$1") ;;
    *) die "usage: build.sh [arm64-v8a|x86_64|host|all]" ;;
esac

command -v docker >/dev/null || die "docker is required"
[[ $BUILD_TYPE != release || $CONTRACT_CHECK != 0 ]] || die "release builds cannot skip the contract check"

hash_files() {
    find "$@" -type f 2>/dev/null | LC_ALL=C sort | xargs -r cat | sha256sum | cut -d' ' -f1
}

hash_strings() {
    printf '%s\n' "$@" | sha256sum | cut -d' ' -f1
}

bash "$SCRIPT_DIR/fetch-core.sh"
# shellcheck source=/dev/null
source "$CACHE_DIR/core-source.resolved.env"

mkdir -p "$SCRIPT_DIR/patches"
PATCH_SET_SHA256=$(hash_files "$SCRIPT_DIR/patches")
BUILDER_SHA256=$(hash_files "$SCRIPT_DIR/docker")
CONTRACT_SHA256=$(hash_files "$SCRIPT_DIR/contract" "$SCRIPT_DIR/contract-check.py")

docker build -q -t "$IMAGE" \
    --build-arg NDK_VERSION="$NDK_VERSION" --build-arg NDK_SHA256="$NDK_SHA256" \
    -f "$SCRIPT_DIR/docker/Dockerfile.ndk" "$SCRIPT_DIR/docker" >/dev/null
BUILDER_IMAGE_ID=$(docker image inspect --format '{{.Id}}' "$IMAGE")

mkdir -p "$CACHE_DIR/depends/sources" "$CACHE_DIR/depends/built"

container_user=(--user "$(id -u):$(id -g)" -e HOME=/tmp)

build_abi() {
    local abi=$1 out=$OUT_DIR/$1 binary=libelektrond.so stamp
    [[ $abi == host ]] && binary=elektrond
    stamp=$(hash_strings "$CORE_SHA" "$PATCH_SET_SHA256" "$BUILDER_SHA256" "$NDK_VERSION" "$ANDROID_API" "$abi")

    if [[ -f $out/$binary && -f $out/manifest.env ]] && grep -qx "STAMP=$stamp" "$out/manifest.env"; then
        echo "build: $abi is up to date for ${CORE_SHA:0:12}"
        return
    fi

    echo "build: $abi from ${CORE_SHA:0:12} ($CORE_REF_NAME)"
    rm -rf "$out"
    mkdir -p "$out" "$CACHE_DIR/build-$abi"
    docker run --rm "${container_user[@]}" \
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
NODE_FASTPRUNE_DEFAULT=$NODE_FASTPRUNE_DEFAULT
LIB_SHA256=$(cat "$out/$binary.sha256")
MANIFEST
}

run_contract_check() {
    local out=$OUT_DIR/host stamp
    stamp=$(hash_strings "$(grep '^STAMP=' "$out/manifest.env")" "$CONTRACT_SHA256")
    if [[ -f $out/contract.ok ]] && grep -qx "$stamp" "$out/contract.ok"; then
        echo "build: contract check already passed for ${CORE_SHA:0:12}"
        return
    fi
    rm -f "$out/contract.ok"
    local strict_tiers=()
    [[ $BUILD_TYPE == release ]] && strict_tiers=(--strict-tiers)
    echo "build: running contract check"
    docker run --rm "${container_user[@]}" \
        -v "$out:/host:ro" -v "$SCRIPT_DIR:/native-build:ro" \
        "$IMAGE" python3 -I /native-build/contract-check.py /host/elektrond "${strict_tiers[@]}" \
        || die "contract check failed, the Android build is not started"
    echo "$stamp" > "$out/contract.ok"
}

for abi in "${ABIS[@]}"; do
    if [[ $abi != host && $CONTRACT_CHECK != 0 ]]; then
        build_abi host
        run_contract_check
    fi
    build_abi "$abi"
    if [[ $abi == host ]]; then
        [[ $CONTRACT_CHECK == 0 ]] || run_contract_check
    else
        OUT_DIR=$OUT_DIR bash "$SCRIPT_DIR/install-core.sh" "$abi"
    fi
done
