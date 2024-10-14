package com.kdroid.composetray.tray.api

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.window.ApplicationScope
import com.kdroid.composetray.menu.api.TrayMenuBuilder

@Composable
fun ApplicationScope.ComposeNativeTray(iconPath: String, windowsIconPath: String = iconPath, tooltip : String, menuContent: TrayMenuBuilder.() -> Unit) {
    LaunchedEffect(Unit) {
        NativeTray(
            iconPath = iconPath,
            windowsIconPath = windowsIconPath,
            tooltip = tooltip,
            menuContent = menuContent
        )
    }
}