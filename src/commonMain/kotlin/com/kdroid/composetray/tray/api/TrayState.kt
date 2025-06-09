package com.kdroid.composetray.tray.api

import androidx.compose.runtime.Composable
import com.kdroid.composetray.menu.api.TrayMenuBuilder
import com.kdroid.composetray.utils.IconRenderProperties

/**
 * Holds the native tray instance and exposes update APIs.
 */
class TrayState internal constructor(private val nativeTray: NativeTray) {

    fun updateTooltip(text: String) {
        nativeTray.updateTooltip(text)
    }

    fun updateMenuItems(menuContent: (TrayMenuBuilder.() -> Unit)?, primaryAction: (() -> Unit)?, primaryActionLinuxLabel: String) {
        nativeTray.updateMenu(menuContent, primaryAction, primaryActionLinuxLabel)
    }

    fun updateIconContent(iconContent: @Composable () -> Unit, properties: IconRenderProperties) {
        nativeTray.updateIconContent(iconContent, properties)
    }

    fun dispose() {
        nativeTray.dispose()
    }
}
