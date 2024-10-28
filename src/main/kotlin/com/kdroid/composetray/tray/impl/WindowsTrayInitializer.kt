package com.kdroid.composetray.tray.impl

import com.kdroid.composetray.lib.windows.WindowsTrayManager
import com.kdroid.composetray.menu.api.TrayMenuBuilder
import com.kdroid.composetray.menu.impl.WindowsTrayMenuBuilderImpl

object WindowsTrayInitializer {

    private var trayMenuImpl: WindowsTrayMenuBuilderImpl? = null

    fun initialize(iconPath: String, tooltip: String, onLeftClick: (() -> Unit)? = null, menuContent: (TrayMenuBuilder.() -> Unit)? = null) {
        val windowsTrayManager = WindowsTrayManager(iconPath, tooltip, onLeftClick)
        // Create an instance of WindowsTrayMenuImpl and apply the menu content
        trayMenuImpl = WindowsTrayMenuBuilderImpl(iconPath, tooltip, onLeftClick).apply {
            menuContent?.let { it() }
        }
        val menuItems = trayMenuImpl!!.build()

        // Add each menu item to WindowsTrayManager
        menuItems.forEach { windowsTrayManager.addMenuItem(it) }

        // Start the Windows tray
        windowsTrayManager.startTray()
    }

    fun dispose() {
        trayMenuImpl?.dispose()
    }
}
