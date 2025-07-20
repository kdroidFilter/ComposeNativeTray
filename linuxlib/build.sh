#!/bin/bash
echo "=== Starting compilation for Linux x86-64 in MinSizeRel mode ==="

echo
echo "=== Configuration (MinSizeRel) ==="
cmake -B build-x86-64 -DCMAKE_BUILD_TYPE=MinSizeRel .
if [ $? -ne 0 ]; then
    echo "Error during configuration"
    exit 1
fi

echo
echo "=== Compilation (MinSizeRel) ==="
cmake --build build-x86-64 --config MinSizeRel
if [ $? -ne 0 ]; then
    echo "Error during compilation"
    exit 1
fi

# Create the destination directory if it doesn't exist
mkdir -p ../src/commonMain/resources/linux-x86-64

# Copy the library to the resources directory
echo
echo "=== Copying library to resources directory ==="
cp build-x86-64/libtray.so ../src/commonMain/resources/linux-x86-64/
if [ $? -ne 0 ]; then
    echo "Error copying library to resources directory"
    exit 1
fi

echo
echo "=== Compilation completed successfully in MinSizeRel mode ==="
echo
echo "Linux x86-64 SO: ../src/commonMain/resources/linux-x86-64/libtray.so"