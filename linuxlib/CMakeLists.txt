cmake_minimum_required(VERSION 3.13 FATAL_ERROR)

project(tray LANGUAGES C CXX)

# Generate 'compile_commands.json' for clang_complete
set(CMAKE_COLOR_MAKEFILE ON)
set(CMAKE_EXPORT_COMPILE_COMMANDS ON)

find_package(PkgConfig REQUIRED)

# Sources for libtray - Linux only
list(APPEND SRCS
		${CMAKE_CURRENT_SOURCE_DIR}/tray_linux.cpp
		${CMAKE_CURRENT_SOURCE_DIR}/QtTrayMenu.cpp
)

add_library(tray SHARED ${SRCS})
set_property(TARGET tray PROPERTY C_STANDARD 99)
target_compile_definitions(tray PRIVATE TRAY_EXPORTS)
set_target_properties(tray PROPERTIES C_VISIBILITY_PRESET hidden)

# Linux Qt6 configuration
set_property(SOURCE ${CMAKE_CURRENT_SOURCE_DIR}/QtTrayMenu.cpp PROPERTY LANGUAGE CXX)
set_property(SOURCE ${CMAKE_CURRENT_SOURCE_DIR}/tray_linux.cpp PROPERTY LANGUAGE CXX)
find_package(Qt6 COMPONENTS Core Widgets Gui REQUIRED)
target_link_libraries(tray PRIVATE Qt6::Widgets Qt6::Core Qt6::Gui)
set_target_properties(tray PROPERTIES LINKER_LANGUAGE CXX AUTOMOC ON)
target_compile_definitions(tray PRIVATE TRAY_QT6=1)

INSTALL(TARGETS tray DESTINATION lib)
INSTALL(FILES tray.h DESTINATION include)

# Tray Example
add_executable(tray_example ${CMAKE_CURRENT_SOURCE_DIR}/example.c)
target_link_libraries(tray_example PRIVATE tray)
add_dependencies(tray_example tray)

# Copy Linux icon files
configure_file(${CMAKE_CURRENT_LIST_DIR}/icon-24px.png ${CMAKE_BINARY_DIR}/icon-24px.png COPYONLY)
configure_file(${CMAKE_CURRENT_LIST_DIR}/icon2-24px.png ${CMAKE_BINARY_DIR}/icon2-24px.png COPYONLY)