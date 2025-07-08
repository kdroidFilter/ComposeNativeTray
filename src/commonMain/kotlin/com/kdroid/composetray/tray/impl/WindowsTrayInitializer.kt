package com.kdroid.composetray.tray.impl

import com.kdroid.composetray.lib.windows.WindowsTrayManager
import com.kdroid.composetray.menu.api.TrayMenuBuilder
import com.kdroid.composetray.menu.impl.WindowsTrayMenuBuilderImpl

object WindowsTrayInitializer {

    private var trayMenuImpl: WindowsTrayMenuBuilderImpl? = null
    private var trayManager: WindowsTrayManager? = null
    private var iconPath: String = ""
    private var tooltip: String = ""
    private var clickCallback: (() -> Unit)? = null

    fun initialize(iconPath: String, tooltip: String, onLeftClick: (() -> Unit)? = null, menuContent: (TrayMenuBuilder.() -> Unit)? = null) {
        this.iconPath = iconPath
        this.tooltip = tooltip
        this.clickCallback = onLeftClick
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
        trayManager = windowsTrayManager
    }

    fun dispose() {
        trayMenuImpl?.dispose()
        trayManager?.stopTray()
        trayManager = null
    }

    fun updateTooltip(text: String) {
        tooltip = text
        trayManager?.updateTooltip(text)
    }

    fun updateIcon(iconPath: String) {
        this.iconPath = iconPath
        trayManager?.updateIcon(iconPath)
    }

    fun updateMenu(menuContent: (TrayMenuBuilder.() -> Unit)?) {
        trayMenuImpl = WindowsTrayMenuBuilderImpl(iconPath, tooltip, clickCallback).apply {
            menuContent?.let { it() }
        }
        val items = trayMenuImpl!!.build()
        trayManager?.updateMenuItems(items)
    }
}
