#!/usr/bin/env bash
# Runs inside the builder image. Expects: ABI, JOBS and, for Android ABIs, ANDROID_API.
# Directories (defaults match the container mounts): SRC elektron-net checkout, PATCHES, CACHE, BUILD, OUT.
set -euo pipefail

: "${ABI:?}" "${JOBS:?}"
SRC=${SRC:-/src}
PATCHES=${PATCHES:-/patches}
CACHE=${CACHE:-/cache}
BUILD=${BUILD:-/build}
OUT=${OUT:-/out}

CMAKE_OPTIONS=(
    -DCMAKE_BUILD_TYPE=Release
    -DBUILD_DAEMON=ON -DBUILD_BITCOIN_BIN=OFF -DBUILD_CLI=OFF -DBUILD_GUI=OFF
    -DBUILD_TESTS=OFF -DBUILD_TX=OFF -DBUILD_UTIL=OFF -DBUILD_BENCH=OFF
    -DBUILD_FUZZ_BINARY=OFF -DBUILD_KERNEL_LIB=OFF
    -DENABLE_WALLET=ON -DENABLE_IPC=OFF -DWITH_ZMQ=OFF -DWITH_USDT=OFF
    -DINSTALL_MAN=OFF -DWITH_CCACHE=OFF -DREDUCE_EXPORTS=ON
)

shopt -s nullglob
for patch in "$PATCHES"/*.patch; do
    git -C "$SRC" apply --check "$patch"
    git -C "$SRC" apply "$patch"
done
shopt -u nullglob

find_daemon() {
    find "$BUILD/tree" -name elektrond -type f -perm -u+x | head -n 1
}

# The host build runs the contract check, the Android builds ship in the APK
if [[ $ABI == host ]]; then
    cmake -S "$SRC" -B "$BUILD/tree" -G Ninja "${CMAKE_OPTIONS[@]}"
    cmake --build "$BUILD/tree" --target elektrond -j"$JOBS"
    DAEMON=$(find_daemon)
    [[ -n $DAEMON ]] || { echo "elektrond not found in build tree" >&2; exit 1; }
    install -m 0755 "$DAEMON" "$OUT/elektrond"
    sha256sum "$OUT/elektrond" | cut -d' ' -f1 > "$OUT/elektrond.sha256"
    exit 0
fi

: "${ANDROID_API:?}"
case $ABI in
    arm64-v8a) TRIPLE=aarch64-linux-android ;;
    x86_64) TRIPLE=x86_64-linux-android ;;
    *) echo "unsupported ABI '$ABI'" >&2; exit 2 ;;
esac

BIN=$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin
CC_BIN=$BIN/${TRIPLE}${ANDROID_API}-clang
CXX_BIN=$BIN/${TRIPLE}${ANDROID_API}-clang++
PAGE_FLAGS="-Wl,-z,max-page-size=16384"

# CMake adds --target=<host> to every compile, and without the API level clang cannot find the NDK start files and libc++
grep -q "COMPILER_TARGET @host@${ANDROID_API}" "$SRC/depends/toolchain.cmake.in" \
    || sed -i -E "s/(CMAKE_(C|CXX|OBJCXX)_COMPILER_TARGET @host@)/\\1${ANDROID_API}/" "$SRC/depends/toolchain.cmake.in"
grep -c "COMPILER_TARGET @host@${ANDROID_API}" "$SRC/depends/toolchain.cmake.in" | grep -qx 3 || { echo "toolchain template was not patched" >&2; exit 1; }
grep -n "COMPILER_TARGET" "$SRC/depends/toolchain.cmake.in"
grep -q "COMPILER_TARGET=\$(host)${ANDROID_API}" "$SRC/depends/funcs.mk" \
    || sed -i -E "s/(-DCMAKE_(C|CXX)_COMPILER_TARGET=\\\$\(host\))/\\1${ANDROID_API}/" "$SRC/depends/funcs.mk"
grep -c "COMPILER_TARGET=\$(host)${ANDROID_API}" "$SRC/depends/funcs.mk" | grep -qx 2 || { echo "funcs.mk was not patched" >&2; exit 1; }
grep -n "COMPILER_TARGET" "$SRC/depends/funcs.mk"

make -C "$SRC/depends" -j"$JOBS" \
    HOST="$TRIPLE" NO_QT=1 NO_QR=1 NO_ZMQ=1 NO_USDT=1 NO_IPC=1 \
    CC="$CC_BIN" CXX="$CXX_BIN" \
    AR="$BIN/llvm-ar" RANLIB="$BIN/llvm-ranlib" NM="$BIN/llvm-nm" \
    STRIP="$BIN/llvm-strip" OBJCOPY="$BIN/llvm-objcopy" OBJDUMP="$BIN/llvm-objdump" \
    SOURCES_PATH="$CACHE/sources" BASE_CACHE="$CACHE/built"

# The depends toolchain targets the bare triple, which drops the API level
cat > "$BUILD/android-toolchain.cmake" <<TOOLCHAIN
include("$SRC/depends/$TRIPLE/toolchain.cmake")
set(CMAKE_C_COMPILER_TARGET ${TRIPLE}${ANDROID_API})
set(CMAKE_CXX_COMPILER_TARGET ${TRIPLE}${ANDROID_API})
TOOLCHAIN

cmake -S "$SRC" -B "$BUILD/tree" -G Ninja \
    -DCMAKE_TOOLCHAIN_FILE="$BUILD/android-toolchain.cmake" \
    "${CMAKE_OPTIONS[@]}" \
    -DCMAKE_EXE_LINKER_FLAGS="$PAGE_FLAGS -static-libstdc++"

cmake --build "$BUILD/tree" --target elektrond -j"$JOBS"

DAEMON=$(find_daemon)
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

install -m 0755 "$DAEMON" "$OUT/libelektrond.so"
sha256sum "$OUT/libelektrond.so" | cut -d' ' -f1 > "$OUT/libelektrond.so.sha256"
