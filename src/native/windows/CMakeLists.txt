cmake_minimum_required(VERSION 3.13 FATAL_ERROR)

project(tray LANGUAGES C CXX)

# Define the C++ standard
set(CMAKE_CXX_STANDARD 17)

# Allow overriding the output base directory via environment variable
# (used by CI to write artifacts to build/nativeLibs instead of src/jvmMain/resources)
if(DEFINED ENV{NATIVE_LIBS_OUTPUT_DIR})
    set(BASE_OUTPUT_DIR "$ENV{NATIVE_LIBS_OUTPUT_DIR}")
else()
    set(BASE_OUTPUT_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../jvmMain/resources/composetray/native")
endif()

# Check target architecture
if(CMAKE_GENERATOR_PLATFORM STREQUAL "x64" OR CMAKE_GENERATOR_PLATFORM STREQUAL "")
    set(TARGET_ARCH "x64")
    set(OUTPUT_DIR "${BASE_OUTPUT_DIR}/win32-x86-64")
    add_compile_options("/arch:AVX2")
elseif(CMAKE_GENERATOR_PLATFORM STREQUAL "ARM64")
    set(TARGET_ARCH "ARM64")
    set(OUTPUT_DIR "${BASE_OUTPUT_DIR}/win32-arm64")
    add_compile_options("/arch:arm64")
else()
    message(FATAL_ERROR "Unsupported architecture: ${CMAKE_GENERATOR_PLATFORM}")
endif()

# Ensure output directory exists
file(MAKE_DIRECTORY ${OUTPUT_DIR})

# Add sources for libtray
list(APPEND SRCS ${CMAKE_CURRENT_SOURCE_DIR}/tray_windows.c)

# Create the shared library
add_library(tray SHARED ${SRCS})
set_property(TARGET tray PROPERTY C_STANDARD 99)
target_compile_definitions(tray PRIVATE TRAY_EXPORTS)
set_target_properties(tray PROPERTIES C_VISIBILITY_PRESET hidden)

# Set necessary preprocessor definitions
target_compile_definitions(tray PRIVATE TRAY_WINAPI=1 WIN32_LEAN_AND_MEAN NOMINMAX)

# Link to Shell32.lib for Windows platform
if(WIN32)
    target_link_libraries(tray PRIVATE shell32)
endif()

# Define the installation path
INSTALL(TARGETS tray DESTINATION lib)

IF (NOT WIN32)
    INSTALL(FILES tray.h DESTINATION include)
ENDIF ()

# Ensure the output directory exists for the architecture
message(STATUS "Target architecture: ${TARGET_ARCH}")
message(STATUS "Output directory: ${OUTPUT_DIR}")

# Ensure the output directory exists
file(MAKE_DIRECTORY ${OUTPUT_DIR})

# Post-build step to copy DLL
add_custom_command(TARGET tray POST_BUILD
        COMMAND ${CMAKE_COMMAND} -E copy $<TARGET_FILE:tray> ${OUTPUT_DIR}/tray.dll)

# Set MinSizeRel options to reduce size
set(CMAKE_BUILD_TYPE MinSizeRel)

# Add any further optimizations for MinSizeRel
if(CMAKE_BUILD_TYPE STREQUAL "MinSizeRel")
    target_compile_options(tray PRIVATE "/O1")  # Optimize for size
    set(CMAKE_EXE_LINKER_FLAGS "${CMAKE_EXE_LINKER_FLAGS} /OPT:REF /OPT:ICF")  # Remove unused code and merge identical functions
endif()

# Ajout de l'exemple en français
add_executable(exemple ${CMAKE_CURRENT_SOURCE_DIR}/example.c)
target_link_libraries(exemple PRIVATE tray)
set_property(TARGET exemple PROPERTY C_STANDARD 99)
