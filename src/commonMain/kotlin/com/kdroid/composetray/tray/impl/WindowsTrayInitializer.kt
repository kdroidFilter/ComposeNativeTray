package com.kdroid.composetray.tray.impl

import com.kdroid.composetray.lib.windows.WindowsTrayManager
import com.kdroid.composetray.menu.api.TrayMenuBuilder
import com.kdroid.composetray.menu.impl.WindowsTrayMenuBuilderImpl

object WindowsTrayInitializer {

    private var trayManager: WindowsTrayManager? = null

    fun initialize(iconPath: String, tooltip: String, onLeftClick: (() -> Unit)? = null, menuContent: (TrayMenuBuilder.() -> Unit)? = null) {
        val windowsTrayManager = WindowsTrayManager(iconPath, tooltip, onLeftClick)
        trayManager = windowsTrayManager
        // Create an instance of WindowsTrayMenuImpl and apply the menu content
        val trayMenuImpl = WindowsTrayMenuBuilderImpl(iconPath, tooltip, onLeftClick).apply {
            menuContent?.let { it() }
        }
        val menuItems = trayMenuImpl.build()

        // Add each menu item to WindowsTrayManager
        menuItems.forEach { windowsTrayManager.addMenuItem(it) }

        // Start the Windows tray
        windowsTrayManager.startTray()
    }

    fun update(iconPath: String, tooltip: String, onLeftClick: (() -> Unit)? = null, menuContent: (TrayMenuBuilder.() -> Unit)? = null) {
        trayManager?.stopTray()
        initialize(iconPath, tooltip, onLeftClick, menuContent)
    }

    fun dispose() {
        trayManager?.stopTray()
        trayManager = null
    }
}