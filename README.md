# 🛠️ Compose Native Tray Library

<p align="center">
  <img src="screenshots/logo.webp" alt="logo" width="300">
</p>


**Compose Native Tray** is a Kotlin library that provides a simple way to create system tray applications with native support for Linux, Windows, and macOS. This library was created to address several issues with the Compose for Desktop tray, including poor HDPI support on Windows and Linux, as well as the outdated appearance of the tray on Linux, which resembled Windows 95. In addition to these fixes, ComposeTray also adds support for checkable items, dividers, submenus, and even nested submenus, offering a more feature-rich and modern solution. The Linux implementation uses GTK, while macOS uses AWT, and the Windows implementation is based on native system calls. Additionally, it allows you to enable or disable individual tray items dynamically. This library allows you to add a system tray icon, tooltip, and menu with various options in a Kotlin DSL-style syntax.

> **Warning**: This library is highly experimental, and its APIs are subject to change.

## ✨ Features

- Cross-platform support for Linux, Windows, and macOS.
- DSL-style syntax to define tray menus with ease.
- Supports standard items, submenus, dividers, and checkable items.
- Ability to enable/disable menu items dynamically.
- Corrects issues with the [Compose for Desktop tray](https://github.com/JetBrains/compose-multiplatform/blob/master/tutorials/Tray_Notifications_MenuBar_new), particularly HDPI support on Windows and Linux.
- Improves the appearance of the tray on Linux, which previously resembled Windows 95.
- Adds support for checkable items, dividers, and submenus, including nested submenus.
- Supports primary action for Windows, macOS, and Linux.
    - On Windows and macOS, the primary action is triggered by a left-click on the tray icon.
    - On Linux, due to the limitations of `libappindicator`, the primary action creates an item at the top of the context menu (with a customizable label).

## 🚀 Getting Started

### 📦 Installation

To use the ComposeTray library, add it as a dependency in your `build.gradle.kts` file:

```kotlin
dependencies {
    implementation("io.github.kdroidfilter:composenativetray:0.3.0")
}
```

### 🛠️ Usage

Here's a basic example of how to use the ComposeTray library to create a system tray icon with a menu:
The `Tray` must be executed inside an Application Scope, like this:

````kotlin
application {
    val iconPath = Paths.get("src/test/resources/icon.png").toAbsolutePath().toString()
    val windowsIconPath = Paths.get("src/test/resources/icon.ico").toAbsolutePath().toString()
    Tray(
        iconPath = iconPath,
        windowsIconPath = windowsIconPath,
        tooltip = "My Application",
        primaryAction = {
            Log.i(logTag, "Primary action triggered")
        },
        primaryActionLinuxLabel = "Open Application"
    ) {
        SubMenu(label = "Options") {
            Item(label = "Setting 1") {
                Log.i(logTag, "Setting 1 selected")
            }
            SubMenu(label = "Advanced Sub-options") {
                Item(label = "Advanced Option 1") {
                    Log.i(logTag, "Advanced Option 1 selected")
                }
                Item(label = "Advanced Option 2") {
                    Log.i(logTag, "Advanced Option 2 selected")
                }
            }
        }

        Divider()

        SubMenu(label = "Tools") {
            Item(label = "Calculator") {
                Log.i(logTag, "Calculator launched")
            }
            Item(label = "Notepad") {
                Log.i(logTag, "Notepad opened")
            }
        }

        Divider()

        CheckableItem(label = "Enable notifications") { isChecked ->
            Log.i(logTag, "Notifications ${if (isChecked) "enabled" else "disabled"}")
        }

        Divider()

        Item(label = "About") {
            Log.i(logTag, "Application v1.0 - Developed by Elyahou")
        }

        Divider()

        Item(label = "Exit", isEnabled = true) {
            Log.i(logTag, "Exiting the application")
            dispose()
            exitProcess(0)
        }

        Item(label = "Version 1.0.0", isEnabled = false)
    }
}
````

### 📋 Components of the Tray Menu
- **Item**: A standard clickable item that can be enabled or disabled.
- **CheckableItem**: A menu item with a checkbox that can be toggled on or off.
- **SubMenu**: A submenu that can contain multiple items, including other submenus.
- **Divider**: A separator used to visually separate menu items.
- **dispose**: Call to remove the system tray icon and exit gracefully.
- **Primary Action**: An action triggered by a left-click on the tray icon (Windows and macOS) or as a top item in the context menu (Linux).

### 🖥️ Platform-Specific Implementation
The `NativeTray` class automatically detects the operating system and initializes the appropriate tray implementation:
- **Linux**: Uses `LinuxTrayInitializer`, which is implemented using GTK via JNA.
- **Windows**: Uses `WindowsTrayInitializer`, which is based on the [tray library by dmikushin](https://github.com/dmikushin/tray).
- **macOS**: Uses `AwtTrayInitializer`, which is implemented using AWT.

> Note: For Windows, you must use an `.ico` file for the tray icon, while for Linux and macOS, a `.png` file is required.

### 🗂️ Windows DLL Generation
To generate the Windows DLL, execute the following command:
```bash
gcc -shared -o ../resources/win32-x86-64/tray.dll tray_windows.c
````

### 📜 Logging

This example uses the `kmplog` library for logging, which allows you to log messages when certain items are selected or toggled.

## 📸 Screenshots

Here are some screenshots of ComposeTray running on different platforms:

### Windows
![Windows](screenshots/windows.png)

### Ubuntu Gnome
![Gnome](screenshots/gnome.png)

### Ubuntu KDE
![KDE](screenshots/kde.png)

## 📄 License

This library is licensed under the MIT License.

## 🤝 Contributing

Feel free to open issues or pull requests if you find any bugs or have suggestions for new features. If you have other ideas for improvements or new functionalities, please let me know! If you use this library in your project, I would be happy to link to those projects as well.

## ✅ Things to Implement

- Implement a system to check if an instance of the application is already running
- Add the ability to dynamically change the tray icon
- Add the ability to locate the position of the systray (top left, top right, etc.)

## 🙏 Acknowledgements

This library is developed and maintained by Elie G, aiming to provide an easy and cross-platform system tray solution for Kotlin applications.

