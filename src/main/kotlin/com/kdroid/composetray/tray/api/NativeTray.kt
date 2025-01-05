package com.kdroid.composetray.tray.api

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.window.ApplicationScope
import com.kdroid.composetray.menu.api.TrayMenuBuilder
import com.kdroid.composetray.tray.impl.AwtTrayInitializer
import com.kdroid.composetray.tray.impl.LinuxTrayInitializer
import com.kdroid.composetray.tray.impl.WindowsTrayInitializer
import com.kdroid.composetray.utils.OperatingSystem
import com.kdroid.composetray.utils.PlatformUtils
import com.kdroid.composetray.utils.extractToTempIfDifferent
import com.kdroid.kmplog.Log
import com.kdroid.kmplog.d
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

internal class NativeTray(
    iconPath: String,
    windowsIconPath: String = iconPath,
    tooltip: String = "",
    primaryAction: (() -> Unit)?,
    primaryActionLinuxLabel: String,
    menuContent: (TrayMenuBuilder.() -> Unit)? = null
) {
    val trayScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        trayScope.launch {
            when (PlatformUtils.currentOS) {
                OperatingSystem.LINUX -> LinuxTrayInitializer.initialize(iconPath, tooltip, primaryAction, primaryActionLinuxLabel, menuContent)
                OperatingSystem.WINDOWS -> WindowsTrayInitializer.initialize(
                    windowsIconPath,
                    tooltip,
                    primaryAction,
                    menuContent
                )

                OperatingSystem.MAC, OperatingSystem.UNKNOWN ->
                    AwtTrayInitializer.initialize(iconPath, tooltip, primaryAction, menuContent)
            }
        }
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
