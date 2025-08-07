#!/bin/bash

# Exit on error
set -e

echo "Building MacTray library..."

echo "Building for ARM64 (Apple Silicon)..."

swiftc -emit-library -o ../src/commonMain/resources/darwin-aarch64/libMacTray.dylib \
    -module-name MacTray \
    -swift-version 5 \
    -O -whole-module-optimization \
    -framework Foundation \
    -framework Cocoa \
    -Xlinker -rpath -Xlinker @executable_path/../Frameworks \
    -Xlinker -rpath -Xlinker @loader_path/Frameworks \
    tray.swift

echo "Building for x86_64 (Intel)..."

swiftc -emit-library -o ../src/commonMain/resources/darwin-x86-64/libMacTray.dylib \
    -module-name MacTray \
    -swift-version 5 \
    -target x86_64-apple-macosx10.14 \
    -O -whole-module-optimization \
    -framework Foundation \
    -framework Cocoa \
    -Xlinker -rpath -Xlinker @executable_path/../Frameworks \
    -Xlinker -rpath -Xlinker @loader_path/Frameworks \
    tray.swift

echo "Build completed successfully."
