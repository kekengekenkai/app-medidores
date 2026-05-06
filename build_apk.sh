#!/bin/bash
# Build debug APK and install on connected device/emulator
set -e

echo "=== Building debug APK ==="
# Try to use Gradle wrapper if present, otherwise fallback to gradle
if [ -f "./gradlew" ]; then
    echo "Using Gradle wrapper..."
    ./gradlew assembleDebug
else
    echo "Gradle wrapper not found, trying system gradle..."
    gradle :app:assembleDebug
fi

APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
if [ ! -f "$APK_PATH" ]; then
    echo "ERROR: APK not found at $APK_PATH"
    exit 1
fi

echo "=== APK built: $APK_PATH ==="

echo "=== Checking for connected device/emulator ==="
if ! command -v adb &> /dev/null; then
    echo "WARNING: adb not found in PATH. Please ensure Android SDK platform-tools are installed."
    echo "You can install APK manually or set PATH to include adb."
    echo "Example: export PATH=\$PATH:\$ANDROID_HOME/platform-tools"
    exit 0
fi

DEVICES=$(adb devices | grep -v "List of devices" | grep -c "device$")
if [ "$DEVICES" -eq "0" ]; then
    echo "No devices/emulators connected. Please start an emulator or connect a device."
    echo "Then run: adb install -r $APK_PATH"
    exit 0
fi

echo "=== Installing APK on device ==="
adb install -r "$APK_PATH"

echo "=== Launching app ==="
# Replace 'com.example.app' with your actual applicationId
adb shell am start -n com.example.app/.MainActivity

echo "=== Done ==="
echo "APK installed and app launched. Check your device/emulator."
