package com.kdroid.composetray.tray

import com.kdroid.composetray.lib.linux.AppIndicator
import com.kdroid.composetray.lib.linux.AppIndicatorCategory
import com.kdroid.composetray.lib.linux.AppIndicatorStatus
import com.kdroid.composetray.lib.linux.Gtk
import com.kdroid.composetray.lib.windows.WindowsTrayManager
import com.kdroid.composetray.menu.*
import com.kdroid.composetray.utils.OperatingSystem
import com.kdroid.composetray.utils.PlatformUtils
import com.sun.jna.Pointer
import java.awt.*
import javax.swing.ImageIcon
import javax.swing.JPopupMenu

class NativeTray(
    iconPath: String,
    tooltip : String = "",
    menuContent: TrayMenu.() -> Unit
) {

    init {
        when (PlatformUtils.currentOS) {
            OperatingSystem.LINUX -> {
                // Initialize GTK
                Gtk.INSTANCE.gtk_init(0, Pointer.createConstant(0))

                // Create the indicator
                val indicator = AppIndicator.INSTANCE.app_indicator_new(
                    "custom-tray",
                    iconPath,
                    AppIndicatorCategory.APPLICATION_STATUS
                )

                AppIndicator.INSTANCE.app_indicator_set_status(indicator, AppIndicatorStatus.ACTIVE)

                // Build the menu
                val menu = Gtk.INSTANCE.gtk_menu_new()
                LinuxTrayMenuImpl(menu).apply(menuContent)
                AppIndicator.INSTANCE.app_indicator_set_menu(indicator, menu)
                Gtk.INSTANCE.gtk_widget_show_all(menu)

                // Start the GTK loop in a separate thread
                Thread {
                    Gtk.INSTANCE.gtk_main()
                }.start()
            }

            OperatingSystem.WINDOWS -> {
                val windowsTrayManager = WindowsTrayManager(iconPath, tooltip)
                // Create an instance of WindowsTrayMenuImpl and apply the menu content
                val trayMenuImpl = WindowsTrayMenuImpl(iconPath).apply(menuContent)
                val menuItems = trayMenuImpl.build()

                // Add each menu item to WindowsTrayManager
                menuItems.forEach { windowsTrayManager.addMenuItem(it) }

                // Start the Windows tray
                windowsTrayManager.startTray()

            }


            OperatingSystem.MAC -> {
                val systemTray = SystemTray.getSystemTray()
                val popupMenu = PopupMenu()

                // Create the tray icon
                val trayIcon = TrayIcon(Toolkit.getDefaultToolkit().getImage(iconPath), tooltip, popupMenu)
                trayIcon.isImageAutoSize = true
                AwtTrayMenuImpl(popupMenu, trayIcon).apply(menuContent)

                // Add the tray icon to the system tray
                systemTray.add(trayIcon)

            }

            OperatingSystem.UNKNOWN -> {
                // Use Swing for other platforms
                val systemTray = java.awt.SystemTray.getSystemTray()
                val popupMenu = JPopupMenu()
                SwingTrayMenuImpl(popupMenu).apply(menuContent)

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
    }

}
