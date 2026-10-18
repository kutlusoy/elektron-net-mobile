#!/usr/bin/env bash
# Runs inside the builder image. Expects: /src (elektron-net checkout), /patches, /cache, /build, /out.
set -euo pipefail

: "${ABI:?}" "${ANDROID_API:?}" "${JOBS:?}"

case $ABI in
    arm64-v8a) TRIPLE=aarch64-linux-android ;;
    x86_64) TRIPLE=x86_64-linux-android ;;
    *) echo "unsupported ABI '$ABI'" >&2; exit 2 ;;
esac

BIN=$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin
CC_BIN=$BIN/${TRIPLE}${ANDROID_API}-clang
CXX_BIN=$BIN/${TRIPLE}${ANDROID_API}-clang++
PAGE_FLAGS="-Wl,-z,max-page-size=16384"

shopt -s nullglob
for patch in /patches/*.patch; do
    git -C /src apply --check "$patch"
    git -C /src apply "$patch"
done
shopt -u nullglob

make -C /src/depends -j"$JOBS" \
    HOST="$TRIPLE" NO_QT=1 NO_QR=1 NO_ZMQ=1 NO_USDT=1 NO_IPC=1 \
    CC="$CC_BIN" CXX="$CXX_BIN" \
    AR="$BIN/llvm-ar" RANLIB="$BIN/llvm-ranlib" NM="$BIN/llvm-nm" \
    STRIP="$BIN/llvm-strip" OBJCOPY="$BIN/llvm-objcopy" OBJDUMP="$BIN/llvm-objdump" \
    SOURCES_PATH=/cache/sources BASE_CACHE=/cache/built

# The depends toolchain targets the bare triple, which drops the API level
cat > /build/android-toolchain.cmake <<TOOLCHAIN
include("/src/depends/$TRIPLE/toolchain.cmake")
set(CMAKE_C_COMPILER_TARGET ${TRIPLE}${ANDROID_API})
set(CMAKE_CXX_COMPILER_TARGET ${TRIPLE}${ANDROID_API})
TOOLCHAIN

cmake -S /src -B /build/tree -G Ninja \
    -DCMAKE_TOOLCHAIN_FILE=/build/android-toolchain.cmake \
    -DCMAKE_BUILD_TYPE=Release \
    -DBUILD_DAEMON=ON -DBUILD_BITCOIN_BIN=OFF -DBUILD_CLI=OFF -DBUILD_GUI=OFF \
    -DBUILD_TESTS=OFF -DBUILD_TX=OFF -DBUILD_UTIL=OFF -DBUILD_BENCH=OFF \
    -DBUILD_FUZZ_BINARY=OFF -DBUILD_KERNEL_LIB=OFF \
    -DENABLE_WALLET=ON -DENABLE_IPC=OFF -DWITH_ZMQ=OFF -DWITH_USDT=OFF \
    -DINSTALL_MAN=OFF -DWITH_CCACHE=OFF -DREDUCE_EXPORTS=ON \
    -DCMAKE_EXE_LINKER_FLAGS="$PAGE_FLAGS -static-libstdc++"

cmake --build /build/tree --target elektrond -j"$JOBS"

DAEMON=$(find /build/tree -name elektrond -type f -perm -u+x | head -n 1)
[[ -n $DAEMON ]] || { echo "elektrond not found in build tree" >&2; exit 1; }

"$BIN/llvm-strip" --strip-unneeded "$DAEMON"

# Every LOAD segment must be aligned to at least 16 KB
loads=0
while read -r align; do
    loads=$((loads + 1))
    if (( align < 16384 )); then
        echo "elektrond has a LOAD segment aligned below 16 KB ($align)" >&2
        exit 1
    fi
done < <("$BIN/llvm-readelf" -lW "$DAEMON" | awk '$1 == "LOAD" { print $NF }')
(( loads > 0 )) || { echo "no LOAD segments found in elektrond" >&2; exit 1; }

install -m 0755 "$DAEMON" /out/libelektrond.so
sha256sum /out/libelektrond.so | cut -d' ' -f1 > /out/libelektrond.so.sha256
