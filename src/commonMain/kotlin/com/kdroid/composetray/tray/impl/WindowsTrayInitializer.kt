package com.kdroid.composetray.tray.impl

import com.kdroid.composetray.lib.windows.WindowsTrayManager
import com.kdroid.composetray.menu.api.TrayMenuBuilder
import com.kdroid.composetray.menu.impl.WindowsTrayMenuBuilderImpl

object WindowsTrayInitializer {

    private var trayManager: WindowsTrayManager? = null

    fun initialize(iconPath: String, tooltip: String, onLeftClick: (() -> Unit)? = null, menuContent: (TrayMenuBuilder.() -> Unit)? = null) {
        // Create menu items
        val trayMenuImpl = WindowsTrayMenuBuilderImpl(iconPath, tooltip, onLeftClick).apply {
            menuContent?.let { it() }
        }
        val menuItems = trayMenuImpl.build()

        if (trayManager == null) {
            // Create new manager
            val windowsTrayManager = WindowsTrayManager(iconPath, tooltip, onLeftClick)
            trayManager = windowsTrayManager
            windowsTrayManager.initialize(menuItems)
        } else {
            // Update existing manager
            trayManager?.update(iconPath, tooltip, onLeftClick, menuItems)
        }
    }

    fun update(iconPath: String, tooltip: String, onLeftClick: (() -> Unit)? = null, menuContent: (TrayMenuBuilder.() -> Unit)? = null) {
        // Same as initialize - it will handle both cases
        initialize(iconPath, tooltip, onLeftClick, menuContent)
    }

    fun dispose() {
        trayManager?.stopTray()
        trayManager = null
    }
}