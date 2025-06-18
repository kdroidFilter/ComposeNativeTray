package com.kdroid.composetray.tray.api

import androidx.compose.runtime.Composable
import com.kdroid.composetray.menu.api.TrayMenuBuilder
import com.kdroid.composetray.utils.IconRenderProperties

/**
 * Holds the native tray instance and exposes update APIs.
 */
class TrayState internal constructor() {

    internal var nativeTray: NativeTray? = null
        private set

    internal var lastIconHash: Long? = null

    internal fun initIfNeeded(
        iconContent: @Composable () -> Unit,
        renderProps: IconRenderProperties,
        tooltip: String,
        primaryAction: (() -> Unit)?,
        primaryActionLinuxLabel: String,
        menuContent: (TrayMenuBuilder.() -> Unit)?
    ) {
        if (nativeTray == null) {
            nativeTray = NativeTray(
                iconContent = iconContent,
                iconRenderProperties = renderProps,
                tooltip = tooltip,
                primaryAction = primaryAction,
                primaryActionLinuxLabel = primaryActionLinuxLabel,
                menuContent = menuContent
            )
        }
    }

    fun updateTooltip(text: String) {
        nativeTray?.updateTooltip(text)
    }

    fun updateMenuItems(
        menuContent: (TrayMenuBuilder.() -> Unit)?,
        primaryAction: (() -> Unit)?,
        primaryActionLinuxLabel: String
    ) {
        nativeTray?.updateMenu(menuContent, primaryAction, primaryActionLinuxLabel)
    }

    fun updateIconContent(iconContent: @Composable () -> Unit, properties: IconRenderProperties) {
        nativeTray?.updateIconContent(iconContent, properties)
    }

    fun dispose() {
        nativeTray?.dispose()
        nativeTray = null
        lastIconHash = null
    }
}
