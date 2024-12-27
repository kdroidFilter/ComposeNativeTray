package com.kdroid.composetray.tray.api

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.window.ApplicationScope
import com.kdroid.composetray.menu.api.TrayMenuBuilder
import com.kdroid.composetray.tray.impl.AwtTrayInitializer
import com.kdroid.composetray.tray.impl.LinuxTrayInitializer
import com.kdroid.composetray.tray.impl.WindowsTrayInitializer
import com.kdroid.composetray.utils.OperatingSystem
import com.kdroid.composetray.utils.PlatformUtils
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


@Composable
fun ApplicationScope.Tray(
    iconPath: String,
    windowsIconPath: String = iconPath,
    tooltip: String,
    primaryAction: (() -> Unit)? = null,
    primaryActionLinuxLabel: String = "Open",
    menuContent: (TrayMenuBuilder.() -> Unit)? = null
) {
    DisposableEffect(iconPath, windowsIconPath, tooltip, primaryAction, primaryActionLinuxLabel, menuContent) {
        NativeTray(
            iconPath = iconPath,
            windowsIconPath = windowsIconPath,
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
