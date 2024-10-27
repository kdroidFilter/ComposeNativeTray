package com.kdroid.composetray.tray.impl


import com.kdroid.composetray.menu.api.TrayMenuBuilder
import com.kdroid.composetray.menu.impl.AwtTrayMenuBuilderImpl
import java.awt.PopupMenu
import java.awt.SystemTray
import java.awt.Toolkit
import java.awt.TrayIcon
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent

object AwtTrayInitializer {
    fun initialize(
        iconPath: String,
        tooltip: String,
        onLeftClick: (() -> Unit)?,
        menuContent: (TrayMenuBuilder.() -> Unit)?
    ) {
        val systemTray = SystemTray.getSystemTray()
        val popupMenu = PopupMenu()

        // Create the tray icon
        val trayIcon = TrayIcon(Toolkit.getDefaultToolkit().getImage(iconPath), tooltip, popupMenu)
        trayIcon.isImageAutoSize = true
        if (menuContent != null) {
            AwtTrayMenuBuilderImpl(popupMenu, trayIcon).apply(menuContent)
        }
        // Handle the left Click
        if (onLeftClick != null) {
            trayIcon.addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    if (e.button == MouseEvent.BUTTON1) { //
                        onLeftClick.invoke()
                    }
                }
            })
        }

        // Add the tray icon to the system tray
        systemTray.add(trayIcon)
    }
}