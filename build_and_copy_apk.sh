#!/bin/bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
APK_PATH="$ROOT_DIR/app/build/outputs/apk/debug/app-debug.apk"

# Elegir destino (Desktop o Escritorio)
if [ -d "$HOME/Desktop" ]; then
  DEST="$HOME/Desktop"
elif [ -d "$HOME/Escritorio" ]; then
  DEST="$HOME/Escritorio"
else
  DEST="$HOME/Desktop"
fi

echo "Building debug APK..."
./gradlew assembleDebug

if [ ! -f "$APK_PATH" ]; then
  echo "APK no encontrado en $APK_PATH" >&2
  exit 1
fi

echo "Copiando APK a $DEST ..."
cp "$APK_PATH" "$DEST/app-debug.apk"

echo "Hecho. APK en: $DEST/app-debug.apk"
