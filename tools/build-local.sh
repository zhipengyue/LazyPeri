#!/usr/bin/env bash
set -euo pipefail

# Builds a debug APK with the Android SDK only (useful when Gradle is unavailable).
project_dir="$(cd "$(dirname "$0")/.." && pwd)"
sdk_dir="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
platform="$sdk_dir/platforms/android-35/android.jar"
build_tools="$sdk_dir/build-tools/35.0.1"
output_dir="$project_dir/app/build/outputs/apk/debug"
version_code="$(awk '$1 == "versionCode" { print $2; exit }' "$project_dir/app/build.gradle")"
version_name="$(awk '$1 == "versionName" { gsub(/\047/, "", $2); print $2; exit }' "$project_dir/app/build.gradle")"
work_dir="$(mktemp -d)"
trap 'rm -rf "$work_dir"' EXIT

mkdir -p "$work_dir/classes" "$work_dir/dex" "$output_dir"
javac -source 17 -target 17 -cp "$platform" -d "$work_dir/classes" \
    "$project_dir"/app/src/main/java/com/mobiletrackpad/app/*.java
find "$work_dir/classes" -name '*.class' -print0 | \
    xargs -0 "$build_tools/d8" --min-api 28 --lib "$platform" --output "$work_dir/dex"
"$build_tools/aapt2" compile --dir "$project_dir/app/src/main/res" -o "$work_dir/resources.zip"
"$build_tools/aapt2" link -o "$work_dir/unsigned.apk" -I "$platform" \
    --manifest "$project_dir/app/src/main/AndroidManifest.xml" \
    -A "$project_dir/app/src/main/assets" \
    -R "$work_dir/resources.zip" \
    --min-sdk-version 28 --target-sdk-version 35 --version-code "$version_code" --version-name "$version_name"
(cd "$work_dir/dex" && zip -q -j "$work_dir/unsigned.apk" classes.dex)
"$build_tools/zipalign" -f 4 "$work_dir/unsigned.apk" "$work_dir/aligned.apk"

key_store="$project_dir/app/build/debug.keystore"
if [[ ! -f "$key_store" ]]; then
    keytool -genkeypair -noprompt -keystore "$key_store" -storepass android \
        -alias androiddebugkey -keypass android -dname "CN=Android Debug,O=Android,C=US" \
        -keyalg RSA -keysize 2048 -validity 10000 >/dev/null 2>&1
fi
"$build_tools/apksigner" sign --ks "$key_store" --ks-pass pass:android \
    --key-pass pass:android --out "$output_dir/app-debug.apk" "$work_dir/aligned.apk"
"$build_tools/apksigner" verify "$output_dir/app-debug.apk"
echo "$output_dir/app-debug.apk"
