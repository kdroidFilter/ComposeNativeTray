package com.kdroid.composetray.tray.api

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.window.ApplicationScope
import com.kdroid.composetray.menu.api.TrayMenuBuilder
import com.kdroid.composetray.tray.impl.AwtTrayInitializer
import com.kdroid.composetray.tray.impl.LinuxTrayInitializer
import com.kdroid.composetray.tray.impl.WindowsTrayInitializer
import com.kdroid.composetray.utils.ComposableIconUtils
import com.kdroid.composetray.utils.OperatingSystem
import com.kdroid.composetray.utils.PlatformUtils
import com.kdroid.composetray.utils.extractToTempIfDifferent
import com.kdroid.kmplog.Log
import com.kdroid.kmplog.d
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

internal class NativeTray {
    val trayScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Constructor that accepts file paths for icons
     */
    constructor(
        iconPath: String,
        windowsIconPath: String = iconPath,
        tooltip: String = "",
        primaryAction: (() -> Unit)?,
        primaryActionLinuxLabel: String,
        menuContent: (TrayMenuBuilder.() -> Unit)? = null
    ) {
        initializeTray(iconPath, windowsIconPath, tooltip, primaryAction, primaryActionLinuxLabel, menuContent)
    }

    /**
     * Constructor that accepts a Composable for the icon
     */
    constructor(
        iconContent: @Composable () -> Unit,
        tooltip: String = "",
        primaryAction: (() -> Unit)?,
        primaryActionLinuxLabel: String,
        menuContent: (TrayMenuBuilder.() -> Unit)? = null,
        iconWidth: Int = 200,
        iconHeight: Int = 200,
        density: Float = 2f
    ) {
        // Render the composable to PNG file for general use
        val pngIconPath = ComposableIconUtils.renderComposableToPngFile(
            width = iconWidth,
            height = iconHeight,
            density = density,
            content = iconContent
        )
        Log.d("NativeTray", "Generated PNG icon path: $pngIconPath")

        // For Windows, we need an ICO file
        val windowsIconPath = if (PlatformUtils.currentOS == OperatingSystem.WINDOWS) {
            // Create a temporary ICO file
            val tempFile = createTempFile(suffix = ".ico")
            val icoData = ComposableIconUtils.renderComposableToIcoBytes(
                width = iconWidth,
                height = iconHeight,
                density = density,
                content = iconContent
            )
            tempFile.writeBytes(icoData)
            val path = tempFile.absolutePath
            Log.d("NativeTray", "Generated Windows ICO path: $path")
            path
        } else {
            pngIconPath
        }

        initializeTray(pngIconPath, windowsIconPath, tooltip, primaryAction, primaryActionLinuxLabel, menuContent)
    }

    private fun initializeTray(
        iconPath: String,
        windowsIconPath: String,
        tooltip: String,
        primaryAction: (() -> Unit)?,
        primaryActionLinuxLabel: String,
        menuContent: (TrayMenuBuilder.() -> Unit)? = null
    ) {
        trayScope.launch {
            when (PlatformUtils.currentOS) {
                OperatingSystem.LINUX -> {
                    Log.d("NativeTray", "Initializing Linux tray with icon path: $iconPath")
                    LinuxTrayInitializer.initialize(iconPath, tooltip, primaryAction, primaryActionLinuxLabel, menuContent)
                }
                OperatingSystem.WINDOWS -> {
                    Log.d("NativeTray", "Initializing Windows tray with icon path: $windowsIconPath")
                    WindowsTrayInitializer.initialize(
                        windowsIconPath,
                        tooltip,
                        primaryAction,
                        menuContent
                    )
                }
                OperatingSystem.MAC, OperatingSystem.UNKNOWN -> {
                    Log.d("NativeTray", "Initializing AWT tray with icon path: $iconPath")
                    AwtTrayInitializer.initialize(iconPath, tooltip, primaryAction, menuContent)
                }
            }
        }
    }

    /**
     * Creates a temporary file that will be deleted when the JVM exits.
     */
    private fun createTempFile(prefix: String = "tray_icon_", suffix: String): java.io.File {
        val tempFile = java.io.File.createTempFile(prefix, suffix)
        tempFile.deleteOnExit()
        return tempFile
    }
}


/**
 * Configures and displays a system tray icon for the application with platform-specific behavior and menu options.
 *
 * @param iconPath The file path to the tray icon. This should be a valid image file compatible with the platform's tray requirements.
 * @param windowsIconPath The file path to the tray icon specifically for Windows. Defaults to the value of `iconPath`.
 * @param tooltip The tooltip text to be displayed when the user hovers over the tray icon.
 * @param primaryAction An optional callback to be invoked when the tray icon is clicked (handled only on specific platforms).
 * @param primaryActionLinuxLabel The label for the primary action on Linux. Defaults to "Open".
 * @param menuContent A lambda that builds the tray menu using a `TrayMenuBuilder`. Define the menu structure, including items, checkable items, dividers, and submenus.
 */
@Composable
fun ApplicationScope.Tray(
    iconPath: String,
    windowsIconPath: String = iconPath,
    tooltip: String,
    primaryAction: (() -> Unit)? = null,
    primaryActionLinuxLabel: String = "Open",
    menuContent: (TrayMenuBuilder.() -> Unit)? = null
) {
    val absoluteIconPath = remember(iconPath) { extractToTempIfDifferent(iconPath)?.absolutePath.orEmpty() }
    val absoluteWindowsIconPath = remember(iconPath, windowsIconPath) {
        if (windowsIconPath == iconPath) absoluteIconPath
        else extractToTempIfDifferent(windowsIconPath)?.absolutePath.orEmpty()
    }
    DisposableEffect(
        absoluteIconPath,
        absoluteWindowsIconPath,
        tooltip,
        primaryAction,
        primaryActionLinuxLabel,
        menuContent
    ) {
        NativeTray(
            iconPath = absoluteIconPath,
            windowsIconPath = absoluteWindowsIconPath,
            tooltip = tooltip,
            primaryAction = primaryAction,
            primaryActionLinuxLabel = primaryActionLinuxLabel,
            menuContent = menuContent
        )

        onDispose {
            Log.d("NativeTray", "onDispose")
            when (PlatformUtils.currentOS) {
                OperatingSystem.WINDOWS -> WindowsTrayInitializer.dispose()
                OperatingSystem.MAC, OperatingSystem.UNKNOWN -> AwtTrayInitializer.dispose()
                OperatingSystem.LINUX -> LinuxTrayInitializer.dispose()
            }
        }
    }
}

/**
 * Configures and displays a system tray icon for the application with platform-specific behavior and menu options.
 * This version accepts a Composable for the icon instead of file paths.
 *
 * @param iconContent A Composable function that defines the icon to be displayed in the tray.
 * @param tooltip The tooltip text to be displayed when the user hovers over the tray icon.
 * @param primaryAction An optional callback to be invoked when the tray icon is clicked (handled only on specific platforms).
 * @param primaryActionLinuxLabel The label for the primary action on Linux. Defaults to "Open".
 * @param menuContent A lambda that builds the tray menu using a `TrayMenuBuilder`. Define the menu structure, including items, checkable items, dividers, and submenus.
 * @param iconWidth Width of the icon in pixels. Defaults to 200.
 * @param iconHeight Height of the icon in pixels. Defaults to 200.
 * @param density Density factor for rendering. Defaults to 2f.
 */
@Composable
fun ApplicationScope.Tray(
    iconContent: @Composable () -> Unit,
    tooltip: String,
    primaryAction: (() -> Unit)? = null,
    primaryActionLinuxLabel: String = "Open",
    menuContent: (TrayMenuBuilder.() -> Unit)? = null,
) {
    DisposableEffect(
        iconContent,
        tooltip,
        primaryAction,
        primaryActionLinuxLabel,
        menuContent,
    ) {
        NativeTray(
            iconContent = iconContent,
            tooltip = tooltip,
            primaryAction = primaryAction,
            primaryActionLinuxLabel = primaryActionLinuxLabel,
            menuContent = menuContent,
        )

        onDispose {
            Log.d("NativeTray", "onDispose")
            when (PlatformUtils.currentOS) {
                OperatingSystem.WINDOWS -> WindowsTrayInitializer.dispose()
                OperatingSystem.MAC, OperatingSystem.UNKNOWN -> AwtTrayInitializer.dispose()
                OperatingSystem.LINUX -> LinuxTrayInitializer.dispose()
            }
        }
    }
}
