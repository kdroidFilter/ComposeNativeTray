# 🛠️ Compose Native Tray Library

<p align="center">
  <img src="screenshots/logo.png" alt="logo">
</p>
<p align="center">
  <a href="https://central.sonatype.com/artifact/io.github.kdroidfilter/composenativetray"><img src="https://img.shields.io/maven-central/v/io.github.kdroidfilter/composenativetray" alt="Maven Central"></a>
  <a href="https://opensource.org/licenses/MIT"><img src="https://img.shields.io/badge/License-MIT-yellow.svg" alt="License: MIT"></a>
  <a href="https://github.com/kdroidFilter/ComposeNativeTray"><img src="https://img.shields.io/badge/Platform-Linux%20%7C%20Windows%20%7C%20macOS-lightgrey.svg" alt="Platform"></a>
  <a href="https://github.com/kdroidFilter/ComposeNativeTray/commits/main"><img src="https://img.shields.io/github/last-commit/kdroidFilter/ComposeNativeTray" alt="Last Commit"></a>
  <a href="https://kdroidfilter.github.io/ComposeNativeTray/"><img src="https://img.shields.io/badge/docs-Dokka-blue.svg" alt="Documentation"></a>
  <a href="https://github.com/kdroidFilter/ComposeNativeTray/issues"><img src="https://img.shields.io/badge/contributions-welcome-brightgreen.svg" alt="Contributions Welcome"></a>
  <a href="https://github.com/kdroidFilter/ComposeNativeTray/actions"><img src="https://img.shields.io/badge/build-passing-brightgreen.svg" alt="Build Passing"></a>
</p>
**Compose Native Tray** is a Kotlin library that provides a simple way to create system tray applications with native support for Linux, Windows, and macOS. This library was created to address several issues with the Compose for Desktop tray, including poor HDPI support on Windows and Linux, as well as the outdated appearance of the tray on Linux, which resembled Windows 95. In addition to these fixes, ComposeTray also adds support for checkable items, dividers, submenus, and even nested submenus, offering a more feature-rich and modern solution. The Linux implementation uses GTK, while macOS uses AWT, and the Windows implementation is based on native system calls. Additionally, it allows you to enable or disable individual tray items dynamically. This library allows you to add a system tray icon, tooltip, and menu with various options in a Kotlin DSL-style syntax.


## ✨ Features

- Cross-platform support for Linux, Windows, and macOS.
- DSL-style syntax to define tray menus with ease.
- Supports standard items, submenus, dividers, and checkable items.
- Ability to enable/disable menu items dynamically.
- Corrects issues with the [Compose for Desktop tray](https://github.com/JetBrains/compose-multiplatform/blob/master/tutorials/Tray_Notifications_MenuBar_new), particularly HDPI support on Windows and Linux.
- Improves the appearance of the tray on Linux, which previously resembled Windows 95.
- Adds support for checkable items, dividers, and submenus, including nested submenus.
- Supports primary action for Windows, macOS, and Linux.
  - On Windows, the primary action is triggered by a left-click on the tray icon.
  - On macOS, the primary action is triggered by a left-click on the tray icon using a native implementation.
  - On Linux, the primary action is triggered by a left-click on KDE and a double left-click on GNOME, using a dbus-based implementation.
- **Single Instance Management**: Ensures that only one instance of the application can run at a time and allows restoring focus to the running instance when another instance is attempted.
- **Tray Position Detection**: Allows determining the position of the system tray, which helps in positioning related windows appropriately.
- **Compose Recomposition Support**: The tray supports Compose recomposition, making it possible to dynamically show or hide the tray icon, for example:

<p align="center">
  <img src="screenshots/demo.gif" alt="demo">
</p>

  ```kotlin
  var isWindowVisible by remember { mutableStateOf(true) }

  if (!isWindowVisible) {
      Tray(
          // Tray parameters
      )
  }
  ```

## 🚀 Getting Started

### 📦 Installation

To use the ComposeTray library, add it as a dependency in your `build.gradle.kts` file:

```kotlin
dependencies {
  implementation("io.github.kdroidfilter:composenativetray:<version>")
}
```

### 🛠️ Usage

Here's a basic example of how to use the ComposeTray library to create a system tray icon with a menu:
The `Tray` must be executed inside an Application Scope, like this:

> **Important**: When using a Composable for the tray icon, it's crucial to apply the `Modifier.fillMaxSize()` to your Composable. This ensures that the Composable fills the entire image when it's converted to an icon. Without this modifier, your icon may appear smaller than intended or not properly centered.

````kotlin
application {
  Tray(
    iconContent = {
      Icon(
        Icons.Default.Favorite,
        contentDescription = "",
        tint = Color.Unspecified, // If not defined icon will be tinted with LocalContentColor.current
        modifier = Modifier.fillMaxSize() // This is crucial!
      )
    },
    tooltip = "My Application",
    primaryAction = {
      Log.i(logTag, "Primary action triggered")
    }
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
- **Primary Action**: An action triggered by a left-click on the tray icon on Windows and macOS, and by a left-click on KDE or double left-click on GNOME for Linux.

### 🎨 Icon Rendering Customization

The `IconRenderProperties` class allows you to customize how your tray icon is rendered. This is an optional parameter when using the Tray component, with a default value already provided:

```kotlin
iconRenderProperties = IconRenderProperties.forCurrentOperatingSystem()
```

#### Available Factory Methods:

1. **forCurrentOperatingSystem()**: Creates properties optimized for the current operating system:
   - Windows: 32x32 pixels
   - macOS: 44x44 pixels
   - Linux: 24x24 pixels

   ```kotlin
   IconRenderProperties.forCurrentOperatingSystem(
       sceneWidth = 192,    // Optional: Width of the compose scene (default: 192)
       sceneHeight = 192,   // Optional: Height of the compose scene (default: 192)
       density = Density(2f) // Optional: Density for the compose scene (default: 2f)
   )
   ```

2. **withoutScalingAndAliasing()**: Creates properties that don't force icon scaling and aliasing:

   ```kotlin
   IconRenderProperties.withoutScalingAndAliasing(
       sceneWidth = 192,    // Optional: Width of the compose scene (default: 192)
       sceneHeight = 192,   // Optional: Height of the compose scene (default: 192)
       density = Density(2f) // Optional: Density for the compose scene (default: 2f)
   )
   ```

This allows you to fine-tune how your icon appears in the system tray across different platforms.

### 🔄 Single Instance Management

The `SingleInstanceManager` ensures that only one instance of the application is running at a time. When a second instance tries to start, it sends a restore request to the existing instance to bring it to the foreground.

#### **Usage**
To use the `SingleInstanceManager`, include it as follows:

```kotlin
var isWindowVisible by remember { mutableStateOf(true) }

val isSingleInstance = SingleInstanceManager.isSingleInstance(onRestoreRequest = {
  isWindowVisible = true
})

if (!isSingleInstance) {
  exitApplication()
  return@application
}
```

In this example, the `SingleInstanceManager` will check if an instance is already running. If not, it will acquire the lock and start watching for restore requests. If an instance is already running, it will send a restore request to bring the existing window to the foreground, allowing you to focus on the already-running application rather than starting a new instance.

#### Configuration

`SingleInstanceManager` can be configured:
```kotlin
SingleInstanceManager.configuration = Configuration(
    lockFilesDir = Paths.get("path/to/your/app/data/dir/single_instance_manager"),
    appIdentifier = "app_id"
)
```
This is useful when you need single-instance management but want finer-grained control.

By specifying a custom `lockFilesDir`, you limit the scope of single-instance management
from every instance of your app on the whole system to only those that share the specified data directory.

Setting the custom `appIdentifier` can be used for even more granular control.

### 📌 Tray Position Detection

The tray position detection feature allows you to precisely position windows relative to the system tray icon. This feature has been significantly enhanced to provide exact positioning capabilities across all supported platforms.

#### Key Features

- **Precise Icon Coordinates**: The library now captures the exact coordinates of the tray icon, not just the general corner position.
- **Centered Window Positioning**: Windows are positioned precisely relative to the tray icon, centered horizontally on the icon.
- **Cross-Platform Support**: Works on Windows, macOS, and Linux with appropriate fallbacks when precise coordinates aren't available.
- **Automatic Boundary Handling**: Ensures windows stay within screen boundaries while maintaining optimal positioning.

The API provides two main functions:

1. `getTrayPosition()`: Returns the general position of the system tray (TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT).
2. `getTrayWindowPosition(windowWidth: Int, windowHeight: Int)`: Returns a `WindowPosition` that places a window of the specified dimensions precisely relative to the tray icon.

#### Implementation Details

- **Windows**: Uses native Windows API to get the exact notification area icon position.
- **macOS**: Uses Cocoa API to get the exact status item position in the menu bar.
- **Linux**: Captures click coordinates when the tray icon is clicked (left-click on KDE, double left-click on GNOME).

#### **Usage**
To use the tray position detection feature:

```kotlin
val windowWidth = 800
val windowHeight = 600
val windowPosition = getTrayWindowPosition(windowWidth, windowHeight)

Window(
  state = rememberWindowState(
    width = windowWidth.dp,
    height = windowHeight.dp,
    position = windowPosition
  ),
  // other window parameters
)
```

In this example, `getTrayWindowPosition` determines the optimal position for the window based on the exact location of the tray icon. The window will be centered horizontally on the icon and positioned vertically based on whether the tray is at the top or bottom of the screen, creating a seamless and user-friendly experience.

## 📸 Screenshots

Here are some screenshots of ComposeTray running on different platforms:

### Windows
![Windows](screenshots/windows.png)

### MacOS
![Windows](screenshots/mac.png)

### Ubuntu Gnome
![Gnome](screenshots/gnome.png)

### Ubuntu KDE
![KDE](screenshots/kde.png)

## 📄 License

This library is licensed under the MIT License.

The Linux module is available at https://github.com/kdroidFilter/LibLinuxTray and is licensed under the GNU Lesser General Public License (LGPL) version 2.1. As a result, the binary for the Linux module is also under LGPL.

## 🤝 Contributing

Feel free to open issues or pull requests if you find any bugs or have suggestions for new features. If you have other ideas for improvements or new functionalities, please let me know! If you use this library in your project, I would be happy to link to those projects as well.

## ✅ Things to Implement

- ✅ ~~Implement a system to check if an instance of the application is already running~~
- ✅ ~~Add the ability to locate the position of the systray (top left, top right, etc.)~~
- ✅ ~~Add the ability to dynamically change the tray icon~~

## 🙏 Acknowledgements

This library is developed and maintained by Elie G, aiming to provide an easy and cross-platform system tray solution for Kotlin applications.
