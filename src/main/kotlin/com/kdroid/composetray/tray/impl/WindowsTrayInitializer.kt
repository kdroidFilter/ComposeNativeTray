package com.kdroid.composetray.tray.impl

import com.kdroid.composetray.lib.windows.WindowsTrayManager
import com.kdroid.composetray.menu.TrayMenu
import com.kdroid.composetray.menu.impl.WindowsTrayMenuImpl

object WindowsTrayInitializer {
    fun initialize(iconPath: String, tooltip: String, menuContent: TrayMenu.() -> Unit) {
        val windowsTrayManager = WindowsTrayManager(iconPath, tooltip)
        // Create an instance of WindowsTrayMenuImpl and apply the menu content
        val trayMenuImpl = WindowsTrayMenuImpl(iconPath).apply(menuContent)
        val menuItems = trayMenuImpl.build()

        // Add each menu item to WindowsTrayManager
        menuItems.forEach { windowsTrayManager.addMenuItem(it) }

        // Start the Windows tray
        windowsTrayManager.startTray()
    }
}
