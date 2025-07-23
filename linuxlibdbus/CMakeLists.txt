cmake_minimum_required(VERSION 3.10)
project(StatusNotifierItemExample LANGUAGES CXX C)  # Ajout de C pour le support C

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_AUTOMOC ON)  # Enable automatic moc generation
set(CMAKE_INCLUDE_CURRENT_DIR ON)  # Include current directory for generated files

# Qt5
find_package(Qt5 REQUIRED COMPONENTS Core Gui Widgets DBus)

# DBusMenu (pour QMenu via DBus)
find_package(PkgConfig REQUIRED)
pkg_check_modules(DBUSMENU REQUIRED dbusmenu-qt5)

# Génère l’adaptateur D-Bus depuis l’interface XML
qt5_add_dbus_adaptor(
        statusnotifier_adaptor_src
        ${CMAKE_SOURCE_DIR}/org.kde.StatusNotifierItem.xml
        ${CMAKE_SOURCE_DIR}/include/statusnotifieritem.h
        StatusNotifierItem
        statusnotifieritemadaptor
)

# Include paths
include_directories(
        ${CMAKE_SOURCE_DIR}/include
        ${DBUSMENU_INCLUDE_DIRS}
)

# Fichiers sources
set(SOURCES
        src/statusnotifieritem.cpp
        src/dbustypes.cpp
        src/sni_wrapper.cpp  # Ajout du wrapper
        ${statusnotifier_adaptor_src}
)

set(HEADERS
        include/statusnotifieritem.h
        include/dbustypes.h
        include/sni_wrapper.h  # Ajout du header pour le wrapper
)

# S'assurer que le fichier d'adaptateur généré est reconnu
set_source_files_properties(${statusnotifier_adaptor_src} PROPERTIES SKIP_AUTOMOC TRUE)

# Librairie partagée pour JNA
add_library(tray SHARED ${SOURCES} ${HEADERS})
target_link_libraries(tray
        Qt5::Core Qt5::Gui Qt5::Widgets Qt5::DBus
        ${DBUSMENU_LIBRARIES}
)

# Librairie statique pour la démo
add_library(statusnotifier STATIC ${SOURCES} ${HEADERS})
target_link_libraries(statusnotifier
        Qt5::Core Qt5::Gui Qt5::Widgets Qt5::DBus
        ${DBUSMENU_LIBRARIES}
)

# Démo simple en C++
add_executable(tray-demo src/main.cpp)
target_link_libraries(tray-demo statusnotifier)

# Démo en C utilisant le wrapper
add_executable(tray-c-demo src/exemple.c)
target_link_libraries(tray-c-demo tray)