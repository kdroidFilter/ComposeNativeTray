@echo off
echo === Starting compilation for x64 and ARM64 in MinSizeRel mode ===

echo.
echo === x64 Configuration (MinSizeRel) ===
cmake -B build-x64 -A x64 -DCMAKE_BUILD_TYPE=MinSizeRel .
if %ERRORLEVEL% neq 0 (
    echo Error during x64 configuration
    exit /b %ERRORLEVEL%
)

echo.
echo === x64 Compilation (MinSizeRel) ===
cmake --build build-x64 --config MinSizeRel
if %ERRORLEVEL% neq 0 (
    echo Error during x64 compilation
    exit /b %ERRORLEVEL%
)

echo.
echo === ARM64 Configuration (MinSizeRel) ===
cmake -B build-arm64 -A ARM64 -DCMAKE_BUILD_TYPE=MinSizeRel .
if %ERRORLEVEL% neq 0 (
    echo Error during ARM64 configuration
    exit /b %ERRORLEVEL%
)

echo.
echo === ARM64 Compilation (MinSizeRel) ===
cmake --build build-arm64 --config MinSizeRel
if %ERRORLEVEL% neq 0 (
    echo Error during ARM64 compilation
    exit /b %ERRORLEVEL%
)

echo.
echo === Compilation completed successfully for both architectures in MinSizeRel mode ===
