package com.kdroid.composetray.tray.api

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.window.ApplicationScope
import com.kdroid.composetray.menu.api.TrayMenuBuilder
import com.kdroid.composetray.tray.impl.AwtTrayInitializer
import com.kdroid.composetray.tray.impl.LinuxTrayInitializer
import com.kdroid.composetray.tray.impl.WindowsTrayInitializer
import com.kdroid.composetray.utils.OperatingSystem
import com.kdroid.composetray.utils.PlatformUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

val trayScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

internal class NativeTray(
    iconPath: String,
    windowsIconPath: String = iconPath,
    tooltip: String = "",
    menuContent: TrayMenuBuilder.() -> Unit
) {
    init {
        trayScope.launch {
            when (PlatformUtils.currentOS) {
                OperatingSystem.LINUX -> LinuxTrayInitializer.initialize(iconPath, menuContent)
                OperatingSystem.WINDOWS -> WindowsTrayInitializer.initialize(windowsIconPath, tooltip, menuContent)
                OperatingSystem.MAC, OperatingSystem.UNKNOWN -> AwtTrayInitializer.initialize(
                    iconPath,
                    tooltip,
                    menuContent
                )
            }
        }
    }
}


@Composable
fun ApplicationScope.Tray(iconPath: String, windowsIconPath: String = iconPath, tooltip : String, menuContent: TrayMenuBuilder.() -> Unit) {
    LaunchedEffect(Unit) {
        NativeTray(
            iconPath = iconPath,
            windowsIconPath = windowsIconPath,
            tooltip = tooltip,
            menuContent = menuContent
        )
    }
}