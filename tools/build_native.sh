#!/usr/bin/env sh
set -eu
DERMINAL_NDK="${ANDROID_NDK_HOME:-${ANDROID_HOME:-}/ndk/28.2.13676358}"
DERMINAL_CC="$DERMINAL_NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android26-clang"
mkdir -p terminal-emulator/src/main/jniLibs/arm64-v8a
"$DERMINAL_CC" -shared -O3 -fPIC -Wall -Wextra -Werror -Wl,-z,max-page-size=16384 -Wl,-soname,libtermux.so terminal-emulator/src/main/jni/termux.c -o terminal-emulator/src/main/jniLibs/arm64-v8a/libtermux.so
