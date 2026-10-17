#!/usr/bin/env bash
# Cross-builds the private X11 broker JNI library against the staged NDK/libxcb tree.
# Inputs are ignored build products under .cache/x11-broker-01 (see README). No network use.
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/../../.." && pwd)
CACHE="$ROOT/.cache/x11-broker-01"
NDK="$ROOT/.cache/android-sdk/ndk/29.0.14206865/toolchains/llvm/prebuilt/linux-x86_64/bin"
CXX="$NDK/aarch64-linux-android35-clang++"
SOURCE="$ROOT/qualification/probes/x11-broker/app/src/main/cpp/cloudy_x11_broker.cpp"
INCLUDE="$CACHE/android-arm64/include"
LIB="$CACHE/android-arm64/lib"
OUTPUT="$CACHE/jniLibs/arm64-v8a/libcloudy_x11_broker.so"

for input in "$CXX" "$SOURCE" "$INCLUDE/xcb/xcb.h" "$LIB/libxcb.a"; do
  [ -e "$input" ] || { echo "missing broker input: $input" >&2; exit 1; }
done

mkdir -p "$(dirname "$OUTPUT")"
"$CXX" -std=c++17 -O2 -fPIC -shared \
  -I"$INCLUDE" -I"$CACHE/xcb-proto-1.17.0" \
  "$SOURCE" -o "$OUTPUT" \
  "$LIB/libxcb-composite.a" "$LIB/libxcb-damage.a" "$LIB/libxcb-xtest.a" "$LIB/libxcb.a" \
  -ljnigraphics -llog -landroid -lm -lstdc++

echo "built $OUTPUT"
sha256sum "$OUTPUT"
