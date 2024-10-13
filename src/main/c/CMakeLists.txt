cmake_minimum_required(VERSION 3.13 FATAL_ERROR) # target_link_directories

project(tray LANGUAGES C CXX)

list(APPEND CMAKE_MODULE_PATH "${CMAKE_CURRENT_LIST_DIR}/cmake")

# Generate 'compile_commands.json' for clang_complete
set(CMAKE_COLOR_MAKEFILE ON)
set(CMAKE_EXPORT_COMPILE_COMMANDS ON)

if(NOT WIN32)
	find_package (PkgConfig REQUIRED)
else()
    add_compile_definitions(_CRT_SECURE_NO_WARNINGS)
endif()

# Sources for libtray
#list(APPEND SRCS ${CMAKE_CURRENT_SOURCE_DIR}/tray.h)
if(WIN32)
	list(APPEND SRCS ${CMAKE_CURRENT_SOURCE_DIR}/tray_windows.c)
else()
	if(UNIX)
		if(APPLE)
			list(APPEND SRCS ${CMAKE_CURRENT_SOURCE_DIR}/tray_darwin.m)
			set(CMAKE_OSX_ARCHITECTURES "arm64;x86_64" CACHE STRING "Build fat binary for macOS" FORCE)
		else()
			list(APPEND SRCS
					${CMAKE_CURRENT_SOURCE_DIR}/tray_linux.cpp
					${CMAKE_CURRENT_SOURCE_DIR}/QtTrayMenu.cpp
			)
		endif()
	endif()
endif()

add_library(tray SHARED ${SRCS})
set_property(TARGET tray PROPERTY C_STANDARD 99)
target_compile_definitions(tray PRIVATE TRAY_EXPORTS)
set_target_properties(tray PROPERTIES C_VISIBILITY_PRESET hidden)

if(WIN32)
	target_compile_definitions(tray PRIVATE TRAY_WINAPI=1 WIN32_LEAN_AND_MEAN NOMINMAX)
else()
	if(UNIX)
		if(APPLE)
			find_library(COCOA Cocoa REQUIRED)
			target_link_libraries(tray PRIVATE ${COCOA})
			target_compile_definitions(tray PRIVATE TRAY_APPKIT=1)
		else()
			set_property(SOURCE ${CMAKE_CURRENT_SOURCE_DIR}/QtTrayMenu.cpp PROPERTY LANGUAGE CXX)
			set_property(SOURCE ${CMAKE_CURRENT_SOURCE_DIR}/tray_linux.cpp PROPERTY LANGUAGE CXX)
			find_package(Qt6 COMPONENTS Core Widgets Gui REQUIRED)
			target_link_libraries(tray PRIVATE Qt6::Widgets Qt6::Core Qt6::Gui)
			add_compile_options("-v")
			set_target_properties(tray PROPERTIES LINKER_LANGUAGE CXX AUTOMOC ON)
			target_compile_definitions(tray PRIVATE TRAY_QT6=1)
		endif()
	endif()
endif()

INSTALL(TARGETS tray DESTINATION lib)

IF(NOT WIN32)
	INSTALL(FILES tray.h DESTINATION include)
ENDIF()


# Tray Example
add_executable(tray_example ${CMAKE_CURRENT_SOURCE_DIR}/example.c)
target_link_libraries(tray_example PRIVATE tray)
add_dependencies(tray_example tray) # Ensure that tray is built before tray_example

if(WIN32)
	configure_file(${CMAKE_CURRENT_LIST_DIR}/icon.ico ${CMAKE_BINARY_DIR}/icon.ico COPYONLY)
	configure_file(${CMAKE_CURRENT_LIST_DIR}/icon2.ico ${CMAKE_BINARY_DIR}/icon2.ico COPYONLY)
else()
	if(UNIX)
		if(APPLE)
			configure_file(${CMAKE_CURRENT_LIST_DIR}/icon.png ${CMAKE_BINARY_DIR}/icon.png COPYONLY)
			configure_file(${CMAKE_CURRENT_LIST_DIR}/icon2.png ${CMAKE_BINARY_DIR}/icon2.png COPYONLY)
		else ()
			configure_file(${CMAKE_CURRENT_LIST_DIR}/icon-24px.png ${CMAKE_BINARY_DIR}/icon-24px.png COPYONLY)
			configure_file(${CMAKE_CURRENT_LIST_DIR}/icon2-24px.png ${CMAKE_BINARY_DIR}/icon2-24px.png COPYONLY)
		endif()
	endif()
endif ()
