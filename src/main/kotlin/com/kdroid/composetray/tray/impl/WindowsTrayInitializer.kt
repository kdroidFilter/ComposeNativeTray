package com.kdroid.composetray.tray.impl

import com.kdroid.composetray.lib.windows.WindowsTrayManager
import com.kdroid.composetray.menu.api.TrayMenuBuilder
import com.kdroid.composetray.menu.impl.WindowsTrayMenuBuilderImpl

object WindowsTrayInitializer {
    fun initialize(iconPath: String, tooltip: String, menuContent: TrayMenuBuilder.() -> Unit) {
        val windowsTrayManager = WindowsTrayManager(iconPath, tooltip)
        // Create an instance of WindowsTrayMenuImpl and apply the menu content
        val trayMenuImpl = WindowsTrayMenuBuilderImpl(iconPath).apply(menuContent)
        val menuItems = trayMenuImpl.build()

        // Add each menu item to WindowsTrayManager
        menuItems.forEach { windowsTrayManager.addMenuItem(it) }

        // Start the Windows tray
        windowsTrayManager.startTray()
    }
}
