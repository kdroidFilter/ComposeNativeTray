package com.kdroid.composetray.tray.impl


import java.awt.*
import com.kdroid.composetray.menu.impl.AwtTrayMenuBuilderImpl
import com.kdroid.composetray.menu.api.TrayMenuBuilder

object AwtTrayInitializer {
    fun initialize(iconPath: String, tooltip: String, menuContent: TrayMenuBuilder.() -> Unit) {
        val systemTray = SystemTray.getSystemTray()
        val popupMenu = PopupMenu()

        // Create the tray icon
        val trayIcon = TrayIcon(Toolkit.getDefaultToolkit().getImage(iconPath), tooltip, popupMenu)
        trayIcon.isImageAutoSize = true
        AwtTrayMenuBuilderImpl(popupMenu, trayIcon).apply(menuContent)

        // Add the tray icon to the system tray
        systemTray.add(trayIcon)
    }
}