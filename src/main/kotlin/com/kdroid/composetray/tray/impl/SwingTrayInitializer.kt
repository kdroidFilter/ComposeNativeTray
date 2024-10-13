package com.kdroid.composetray.tray.impl

import com.kdroid.composetray.menu.impl.SwingTrayMenuBuilderImpl
import com.kdroid.composetray.menu.api.TrayMenuBuilder
import javax.swing.ImageIcon
import javax.swing.JPopupMenu
import java.awt.*

object SwingTrayInitializer {
    fun initialize(iconPath: String, tooltip: String, menuContent: TrayMenuBuilder.() -> Unit) {
        val systemTray = SystemTray.getSystemTray()
        val popupMenu = JPopupMenu()
        SwingTrayMenuBuilderImpl(popupMenu).apply(menuContent)

        // Create an icon from the icon file path
        val imageIcon = ImageIcon(iconPath)
        val awtImage = imageIcon.image

        // Create the tray icon
        val trayIcon = TrayIcon(awtImage, tooltip)
        trayIcon.isImageAutoSize = true

        // Add a listener to show the context menu
        trayIcon.addActionListener {
            // Ensure the menu is shown when clicking on the tray icon
            val mouseX = MouseInfo.getPointerInfo().location.x
            val mouseY = MouseInfo.getPointerInfo().location.y
            popupMenu.show(null, mouseX, mouseY)
        }

        // Add the tray icon to the system tray
        systemTray.add(trayIcon)
    }
}