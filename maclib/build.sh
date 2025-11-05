#!/bin/bash

# Exit on error
set -e

echo "Building MacTray library..."

OUTPUT_DIR="${NATIVE_LIBS_OUTPUT_DIR}"
echo "output dir for mac is: $OUTPUT_DIR"

echo "Building for ARM64 (Apple Silicon)..."

mkdir -p "$OUTPUT_DIR/darwin-aarch64"
mkdir -p "$OUTPUT_DIR/darwin-x86-64"

swiftc -emit-library -o "$OUTPUT_DIR/darwin-aarch64/libMacTray.dylib" \
    -module-name MacTray \
    -swift-version 5 \
    -target arm64-apple-macosx11.0 \
    -O -whole-module-optimization \
    -framework Foundation \
    -framework Cocoa \
    -Xlinker -rpath -Xlinker @executable_path/../Frameworks \
    -Xlinker -rpath -Xlinker @loader_path/Frameworks \
    tray.swift

echo "Building for x86_64 (Intel)..."

swiftc -emit-library -o "$OUTPUT_DIR/darwin-x86-64/libMacTray.dylib" \
    -module-name MacTray \
    -swift-version 5 \
    -target x86_64-apple-macosx11.0 \
    -O -whole-module-optimization \
    -framework Foundation \
    -framework Cocoa \
    -Xlinker -rpath -Xlinker @executable_path/../Frameworks \
    -Xlinker -rpath -Xlinker @loader_path/Frameworks \
    tray.swift

echo "Build completed successfully."
