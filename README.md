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
  - On Windows ans macOS, the primary action is triggered by a left-click on the tray icon.
  - On Linux, on GNOME the primary action is triggered by a double left-click on the tray icon, while on the majority of other environments, primarily KDE Plasma, it is triggered by a single left-click, similar to Windows and macOS.
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
    },
    primaryActionLabel = "Open Application"
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
- **Primary Action**: An action triggered by a left-click on the tray icon (Windows) or as a top item in the context menu (MacOs and Linux).

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

The `getTrayPosition()` function allows you to determine the current position of the system tray on the screen. This information can be useful for aligning application windows relative to the tray icon.

> **Note**: Currently, on macOS, the `getTrayPosition()` function always returns `TOP_RIGHT`, and the tray position is not dynamically detected. On Windows and Linux, the position is determined dynamically.

Additionally, the `getTrayWindowPosition(windowWidth: Int, windowHeight: Int): WindowPosition` function can be used to determine the appropriate position for a window of specific dimensions, taking into account the location of the tray.

#### **Usage**
To use the tray position functions:

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

In this example, `getTrayWindowPosition` is used to determine the ideal position for the window based on the system tray's location. This helps in creating a seamless and user-friendly experience when displaying windows near the tray icon.

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

## Linux packaging and Qt dependencies

This library uses Qt on Linux. When you package your app for Linux, you must declare the required Qt runtime packages so the OS will install them if necessary.

* Minimal Debian/Ubuntu dependencies:

    * libqt5core5t64
    * libqt5gui5t64
    * libqt5widgets5t64
    * libdbusmenu-qt5-2

> **Heads-up:** In most cases, the only package that will be newly downloaded is **`libdbusmenu-qt5-2`** (≈300 KB). Qt itself is widely used by many desktop applications and often comes preinstalled with the desktop environment, so users typically already have the necessary Qt runtime packages.

### Developer setup on Ubuntu/Debian

If you are developing or running locally on Ubuntu/Debian, ensure the runtime package is installed:

```sh
sudo apt-get install -y libdbusmenu-qt5-2
```

### Link your app to the Qt runtime packages

If you use jpackage (Compose Multiplatform native distributions), we provide a Gradle plugin to declare these dependencies in the generated .deb control file:

* Plugin: [https://github.com/kdroidFilter/GradleComposeDesktopLinuxDeps](https://github.com/kdroidFilter/GradleComposeDesktopLinuxDeps)
* Apply it in your Gradle build (Kotlin DSL):

```kotlin
plugins {
    // ... your existing plugins
    id("io.github.kdroidfilter.compose.linux.packagedeps") version "0.2.0"
}
```

Then configure the Debian dependencies:

```kotlin
linuxDebConfig {
    debDepends.set(
        listOf(
            "libqt5core5t64",
            "libqt5gui5t64",
            "libqt5widgets5t64",
            "libdbusmenu-qt5-2"
        )
    )
}
```

#### Important note about jpackage and distro compatibility

There is a known jpackage issue that can cause incompatibilities between packages built on Ubuntu 24/Debian 13 and those built on Ubuntu 22/Debian 12 (and vice versa). Please see the plugin README for [details](https://github.com/kdroidFilter/GradleComposeDesktopLinuxDeps?tab=readme-ov-file#-known-jpackage-issue-ubuntu-t64-transition)

To support both newer T64 packages and older package names, use alternatives and enable the T64 alternative mode:

```kotlin
linuxDebConfig {
    debDepends.set(
        listOf(
            "libqt5core5t64 | libqt5core5a",
            "libqt5gui5t64 | libqt5gui5",
            "libqt5widgets5t64 | libqt5widgets5",
            "libdbusmenu-qt5-2"
        )
    )
    enableT64AlternativeDeps = true
}
```

### Using Conveyor

If you package with Conveyor, declare the dependencies in your `conveyor.conf`:

```hocon
app.linux.debian.control {
  Depends: [
    "libqt5core5t64 | libqt5core5a",
    "libqt5gui5t64  | libqt5gui5",
    "libqt5widgets5t64 | libqt5widgets5",
    "libdbusmenu-qt5-2"
  ]
}
```

## 🙏 Acknowledgements

This library is developed and maintained by Elie Gambache, aiming to provide an easy and cross-platform system tray solution for Kotlin applications.

## 📄 License

This library is licensed under the MIT License.

## 🤝 Contributing

Feel free to open issues or pull requests if you find any bugs or have suggestions for new features. If you have other ideas for improvements or new functionalities, please let me know! If you use this library in your project, I would be happy to link to those projects as well.
