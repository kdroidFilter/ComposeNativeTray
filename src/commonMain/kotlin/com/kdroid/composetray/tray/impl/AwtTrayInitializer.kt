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
    // Stores the reference to the current TrayIcon
    private var trayIcon: TrayIcon? = null
    private var popupMenu: PopupMenu? = null

    fun isSupported(): Boolean = SystemTray.isSupported()

    /**
     * Initializes the system tray with the specified parameters.
     *
     * @param iconPath Path to the tray icon.
     * @param tooltip Tooltip text for the icon.
     * @param onLeftClick Action to execute on left-click on the icon.
     * @param menuContent Content of the tray menu.
     * @throws IllegalStateException If the system does not support the tray.
     */
    fun initialize(
        iconPath: String,
        tooltip: String,
        onLeftClick: (() -> Unit)?,
        menuContent: (TrayMenuBuilder.() -> Unit)?
    ) {
        if (!isSupported()) {
            throw IllegalStateException("System tray is not supported.")
        }

        // If a trayIcon already exists, remove it before creating a new one
        dispose()

        val systemTray = SystemTray.getSystemTray()
        var popupMenu = PopupMenu()

        // Create the tray icon
        val trayImage = Toolkit.getDefaultToolkit().getImage(iconPath)
        val newTrayIcon = TrayIcon(trayImage, tooltip, popupMenu).apply {
            isImageAutoSize = true

            // Handle the left-click if specified
            onLeftClick?.let { clickAction ->
                addMouseListener(object : MouseAdapter() {
                    override fun mouseClicked(e: MouseEvent) {
                        if (e.button == MouseEvent.BUTTON1) {
                            clickAction()
                        }
                    }
                })
            }
        }

        // Add the menu content if specified
        menuContent?.let {
            AwtTrayMenuBuilderImpl(popupMenu, newTrayIcon).apply(it)
        }

        // Add the icon to the system tray
        systemTray.add(newTrayIcon)

        // Store the reference for future use
        trayIcon = newTrayIcon
        popupMenu = popupMenu
    }

    fun updateTooltip(text: String) {
        trayIcon?.toolTip = text
    }

    fun updateIcon(iconPath: String) {
        trayIcon?.image = Toolkit.getDefaultToolkit().getImage(iconPath)
    }

    fun updateMenu(menuContent: (TrayMenuBuilder.() -> Unit)?) {
        trayIcon?.let { icon ->
            val newMenu = PopupMenu()
            menuContent?.let { AwtTrayMenuBuilderImpl(newMenu, icon).apply(it) }
            icon.popupMenu = newMenu
            popupMenu = newMenu
        }
    }

    /**
     * Disposes of the current tray icon, if it exists.
     */
    fun dispose() {
        trayIcon?.let { icon ->
            SystemTray.getSystemTray().remove(icon)
            trayIcon = null
        }
    }
}