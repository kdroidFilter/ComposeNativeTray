#!/bin/bash

# Exit on any error
set -e

echo "Building Linux systray shared library..."

# Ensure we run from this script's directory (linuxlib)
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

# Use NATIVE_LIBS_OUTPUT_DIR env var if set, otherwise default to src/jvmMain/resources/composetray/native
OUTPUT_DIR="${NATIVE_LIBS_OUTPUT_DIR:-$SCRIPT_DIR/../../jvmMain/resources/composetray/native}"
echo "Output dir for linux is: $OUTPUT_DIR"

# Inform about architecture if provided
if [[ -n "$GOARCH" ]]; then
  echo "Using GOARCH=$GOARCH"
else
  echo "GOARCH not set, defaulting to amd64 (as per Makefile)"
fi

# Build the shared library using the provided Makefile target
make build-so

# Destination directory
DEST_DIR="$OUTPUT_DIR/linux-x86-64"
mkdir -p "$DEST_DIR"

# Copy the generated .so to the destination directory
cp -f dist/libsystray.so "$DEST_DIR/libsystray.so"

echo "Copied dist/libsystray.so to $DEST_DIR/libsystray.so"
echo "Linux build completed successfully."
