package com.kdroid.composetray.tray.impl


import java.awt.*
import com.kdroid.composetray.menu.impl.AwtTrayMenuImpl
import com.kdroid.composetray.menu.TrayMenu

object AwtTrayInitializer {
    fun initialize(iconPath: String, tooltip: String, menuContent: TrayMenu.() -> Unit) {
        val systemTray = SystemTray.getSystemTray()
        val popupMenu = PopupMenu()

        // Create the tray icon
        val trayIcon = TrayIcon(Toolkit.getDefaultToolkit().getImage(iconPath), tooltip, popupMenu)
        trayIcon.isImageAutoSize = true
        AwtTrayMenuImpl(popupMenu, trayIcon).apply(menuContent)

        // Add the tray icon to the system tray
        systemTray.add(trayIcon)
    }
}