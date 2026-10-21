#!/usr/bin/env bash
# Usage: install-core.sh <abi>
# Copies a built libelektrond.so and its manifest into the Android project.
set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
APP_DIR=${APP_DIR:-$SCRIPT_DIR/../app/src/main}
OUT_DIR=${OUT_DIR:-$SCRIPT_DIR/out}
abi=${1:?usage: install-core.sh <abi>}

[[ -f $OUT_DIR/$abi/libelektrond.so ]] || { echo "install-core: no build for $abi in $OUT_DIR" >&2; exit 1; }

mkdir -p "$APP_DIR/jniLibs/$abi" "$APP_DIR/assets/core-manifest"
install -m 0755 "$OUT_DIR/$abi/libelektrond.so" "$APP_DIR/jniLibs/$abi/libelektrond.so"
install -m 0644 "$OUT_DIR/$abi/manifest.env" "$APP_DIR/assets/core-manifest/$abi.env"

rm -f "$APP_DIR/jniLibs/$abi/libtor.so" "$APP_DIR/jniLibs/$abi/libi2pd.so" "$APP_DIR/assets/core-manifest/helpers-$abi.env"
if [[ -f $OUT_DIR/$abi/libtor.so && -f $OUT_DIR/$abi/libi2pd.so ]]; then
    install -m 0755 "$OUT_DIR/$abi/libtor.so" "$APP_DIR/jniLibs/$abi/libtor.so"
    install -m 0755 "$OUT_DIR/$abi/libi2pd.so" "$APP_DIR/jniLibs/$abi/libi2pd.so"
    install -m 0644 "$OUT_DIR/$abi/helpers.env" "$APP_DIR/assets/core-manifest/helpers-$abi.env"
    rm -rf "$APP_DIR/assets/i2pd/certificates"
    mkdir -p "$APP_DIR/assets/i2pd"
    cp -r "$OUT_DIR/$abi/i2pd-certificates" "$APP_DIR/assets/i2pd/certificates"
fi
echo "install-core: $abi installed"
