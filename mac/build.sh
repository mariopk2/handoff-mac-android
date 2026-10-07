#!/bin/bash
# Компилира ClipBridge.app. Нужни са само Xcode Command Line Tools:
#   xcode-select --install
set -e
cd "$(dirname "$0")"

APP="ClipBridge.app"
rm -rf "$APP"
mkdir -p "$APP/Contents/MacOS"

swiftc -O -swift-version 5 \
  -framework Cocoa -framework Network -framework ServiceManagement \
  -o "$APP/Contents/MacOS/ClipBridge" main.swift

cp Info.plist "$APP/Contents/Info.plist"
codesign --force --sign - "$APP"

echo ""
echo "Готово: $(pwd)/$APP"
echo "Премести го в /Applications и го стартирай:"
echo "  mv ClipBridge.app /Applications/ && open /Applications/ClipBridge.app"
