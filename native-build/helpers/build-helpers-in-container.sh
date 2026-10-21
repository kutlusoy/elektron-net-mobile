#!/usr/bin/env bash
# Runs inside the builder image. Builds Tor and i2pd for one Android ABI, together with the libraries they need.
# Expects: ABI, JOBS, ANDROID_API and the pinned dependency versions from helpers-source.env.
# Directories: TOR_SRC and I2PD_SRC checkouts, CACHE for downloads and built dependencies, BUILD, OUT.
set -euo pipefail

: "${ABI:?}" "${JOBS:?}" "${ANDROID_API:?}"
: "${ZLIB_URL:?}" "${ZLIB_SHA256:?}" "${OPENSSL_URL:?}" "${OPENSSL_SHA256:?}"
: "${LIBEVENT_URL:?}" "${LIBEVENT_SHA256:?}" "${BOOST_URL:?}" "${BOOST_SHA256:?}"
TOR_SRC=${TOR_SRC:-/tor}
I2PD_SRC=${I2PD_SRC:-/i2pd}
CACHE=${CACHE:-/cache}
BUILD=${BUILD:-/build}
OUT=${OUT:-/out}

case $ABI in
    arm64-v8a) TRIPLE=aarch64-linux-android OPENSSL_TARGET=android-arm64 BOOST_ARCH=arm ;;
    x86_64) TRIPLE=x86_64-linux-android OPENSSL_TARGET=android-x86_64 BOOST_ARCH=x86 ;;
    *) echo "unsupported ABI '$ABI'" >&2; exit 2 ;;
esac

BIN=$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin
export CC=$BIN/${TRIPLE}${ANDROID_API}-clang
export CXX=$BIN/${TRIPLE}${ANDROID_API}-clang++
export AR=$BIN/llvm-ar RANLIB=$BIN/llvm-ranlib STRIP=$BIN/llvm-strip NM=$BIN/llvm-nm
export PATH=$BIN:$PATH
PAGE_FLAGS="-Wl,-z,max-page-size=16384"
PREFIX=$CACHE/prefix-$ABI
SOURCES=$CACHE/sources
mkdir -p "$PREFIX" "$SOURCES" "$BUILD" "$OUT"

fetch() {
    local url=$1 sha=$2 file=$SOURCES/$(basename "$1")
    [[ -f $file ]] || curl -fsSL -o "$file" "$url"
    echo "$sha  $file" | sha256sum -c - >&2
    echo "$file"
}

unpack() {
    local archive=$1 dir=$BUILD/$2
    rm -rf "$dir"
    mkdir -p "$dir"
    tar -xf "$archive" -C "$dir" --strip-components=1
    echo "$dir"
}

build_zlib() {
    [[ -f $PREFIX/lib/libz.a ]] && return
    local dir
    dir=$(unpack "$(fetch "$ZLIB_URL" "$ZLIB_SHA256")" zlib)
    (cd "$dir" && CHOST=$TRIPLE ./configure --static --prefix="$PREFIX" && make -j"$JOBS" && make install)
}

build_openssl() {
    [[ -f $PREFIX/lib/libssl.a ]] && return
    local dir
    dir=$(unpack "$(fetch "$OPENSSL_URL" "$OPENSSL_SHA256")" openssl)
    (
        cd "$dir"
        export ANDROID_NDK_ROOT=$ANDROID_NDK_HOME
        ./Configure "$OPENSSL_TARGET" -D__ANDROID_API__="$ANDROID_API" no-shared no-tests no-ui-console \
            --prefix="$PREFIX" --libdir=lib --openssldir="$PREFIX/ssl"
        make -j"$JOBS" build_libs
        make install_dev
    )
}

build_libevent() {
    [[ -f $PREFIX/lib/libevent.a ]] && return
    local dir
    dir=$(unpack "$(fetch "$LIBEVENT_URL" "$LIBEVENT_SHA256")" libevent)
    (
        cd "$dir"
        ./configure --host="$TRIPLE" --prefix="$PREFIX" --disable-shared --enable-static --with-pic \
            --disable-openssl --disable-samples --disable-libevent-regress --disable-debug-mode
        make -j"$JOBS"
        make install
    )
}

build_boost() {
    [[ -f $PREFIX/lib/libboost_filesystem.a ]] && return
    local dir
    dir=$(unpack "$(fetch "$BOOST_URL" "$BOOST_SHA256")" boost)
    (
        cd "$dir"
        ./bootstrap.sh --with-toolset=gcc >/dev/null
        cat > user-config.jam <<JAM
using clang : android : $CXX : <archiver>$AR <ranlib>$RANLIB <compileflags>-fPIC <linkflags>$PAGE_FLAGS ;
JAM
        ./b2 -j"$JOBS" --user-config=user-config.jam toolset=clang-android target-os=android \
            architecture="$BOOST_ARCH" address-model=64 link=static runtime-link=shared threading=multi variant=release \
            --with-atomic --with-filesystem --with-program_options --with-system \
            --prefix="$PREFIX" --libdir="$PREFIX/lib" install
    )
}

check_alignment() {
    local file=$1 loads=0 align
    while read -r align; do
        loads=$((loads + 1))
        (( align >= 16384 )) || { echo "$file has a LOAD segment aligned below 16 KB ($align)" >&2; exit 1; }
    done < <("$BIN/llvm-readelf" -lW "$file" | awk '$1 == "LOAD" { print $NF }')
    (( loads > 0 )) || { echo "no LOAD segments found in $file" >&2; exit 1; }
}

# A fully static executable cannot resolve host names on Android (the resolver hooks of bionic are only set up by the dynamic loader)
check_dynamic() {
    local file=$1
    "$BIN/llvm-readelf" -d "$file" | grep -q "(NEEDED)" || { echo "$file is statically linked, DNS would fail on Android" >&2; exit 1; }
}

build_tor() {
    local dir=$BUILD/tor
    rm -rf "$dir"
    cp -a "$TOR_SRC" "$dir"
    (
        cd "$dir"
        ./autogen.sh
        ./configure --host="$TRIPLE" --enable-android \
            --disable-asciidoc --disable-unittests --disable-system-torrc --disable-manpage --disable-html-manual \
            --disable-systemd --disable-seccomp --disable-libscrypt --disable-lzma --disable-zstd --disable-tool-name-check \
            --enable-static-libevent --enable-static-openssl --enable-static-zlib \
            --with-libevent-dir="$PREFIX" --with-openssl-dir="$PREFIX" --with-zlib-dir="$PREFIX" \
            LDFLAGS="$PAGE_FLAGS"
        make -j"$JOBS" src/app/tor
        "$STRIP" --strip-unneeded src/app/tor
    )
    check_alignment "$dir/src/app/tor"
    check_dynamic "$dir/src/app/tor"
    install -m 0755 "$dir/src/app/tor" "$OUT/libtor.so"
    sha256sum "$OUT/libtor.so" | cut -d' ' -f1 > "$OUT/libtor.so.sha256"
}

build_i2pd() {
    local dir=$BUILD/i2pd src=$BUILD/i2pd-src
    rm -rf "$dir" "$src"
    cp -a "$I2PD_SRC" "$src"
    # WITH_STATIC links the libraries statically, which is wanted, but it also adds -static for the whole executable
    sed -i 's/LINK_FLAGS "-static"/LINK_FLAGS ""/' "$src/build/CMakeLists.txt"
    ! grep -q 'LINK_FLAGS "-static"' "$src/build/CMakeLists.txt" || { echo "the i2pd build file still links statically" >&2; exit 1; }
    cmake -S "$src/build" -B "$dir" -G Ninja \
        -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake" \
        -DANDROID_ABI="$ABI" -DANDROID_PLATFORM="android-$ANDROID_API" -DANDROID_STL=c++_static \
        -DCMAKE_BUILD_TYPE=Release -DCMAKE_CXX_FLAGS=-DANDROID_BINARY \
        -DWITH_LIBRARY=OFF -DWITH_BINARY=ON -DWITH_STATIC=ON -DWITH_UPNP=OFF -DWITH_TORRENTS=OFF -DWITH_GIT_VERSION=OFF \
        -DBOOST_ROOT="$PREFIX" -DBoost_NO_SYSTEM_PATHS=ON -DOPENSSL_ROOT_DIR="$PREFIX" -DZLIB_ROOT="$PREFIX" \
        -DCMAKE_FIND_ROOT_PATH="$PREFIX" \
        -DCMAKE_EXE_LINKER_FLAGS="$PAGE_FLAGS"
    cmake --build "$dir" --target i2pd -j"$JOBS"
    local binary
    binary=$(find "$dir" -maxdepth 1 -name i2pd -type f | head -n 1)
    [[ -n $binary ]] || { echo "i2pd not found in build tree" >&2; exit 1; }
    "$STRIP" --strip-unneeded "$binary"
    check_alignment "$binary"
    check_dynamic "$binary"
    install -m 0755 "$binary" "$OUT/libi2pd.so"
    sha256sum "$OUT/libi2pd.so" | cut -d' ' -f1 > "$OUT/libi2pd.so.sha256"
    rm -rf "$OUT/i2pd-certificates"
    cp -r "$src/contrib/certificates" "$OUT/i2pd-certificates"
}

build_zlib
build_openssl
build_libevent
build_boost
build_tor
build_i2pd
