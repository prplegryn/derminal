#!/usr/bin/env sh
set -eu
export ANDROID_HOME="/data/data/com.termux/files/home/.android/sdk"
export LD_LIBRARY_PATH="/data/data/com.termux/files/usr/lib"
exec /data/data/com.termux/files/home/.gradle/wrapper/dists/gradle-8.13-bin/5xuhj0ry160q40clulazy9h7d/gradle-8.13/bin/gradle --no-daemon -Dorg.gradle.native=false -Dorg.gradle.internal.native.integrations.enabled=false -Pandroid.aapt2FromMavenOverride=/data/data/com.termux/files/usr/bin/aapt2 "$@"
