package com.kdroid.composetray.tray

import com.kdroid.composetray.lib.linux.AppIndicator
import com.kdroid.composetray.lib.linux.AppIndicatorCategory
import com.kdroid.composetray.lib.linux.AppIndicatorStatus
import com.kdroid.composetray.lib.linux.Gtk
import com.kdroid.composetray.menu.*
import com.kdroid.composetray.state.TrayState
import com.kdroid.composetray.utils.OperatingSystem
import com.kdroid.composetray.utils.PlatformUtils
import com.sun.jna.Memory
import com.sun.jna.Pointer
import java.awt.*
import java.nio.charset.Charset
import javax.swing.ImageIcon
import javax.swing.JPopupMenu

class TrayMenuBuilder(
    state: TrayState,
    icon: String,
    menuContent: TrayMenu.() -> Unit
) {
    private val indicator: Pointer?
    private val trayIcon: TrayIcon?


    init {
        when (PlatformUtils.currentOS) {
            OperatingSystem.LINUX -> {
                // Initialiser GTK
                Gtk.INSTANCE.gtk_init(0, Pointer.createConstant(0))

                // Créer l'indicateur
                indicator = AppIndicator.INSTANCE.app_indicator_new(
                    "custom-tray",
                    icon,
                    AppIndicatorCategory.APPLICATION_STATUS
                )

                AppIndicator.INSTANCE.app_indicator_set_status(indicator, AppIndicatorStatus.ACTIVE)

                // Construire le menu
                val menu = Gtk.INSTANCE.gtk_menu_new()
                LinuxTrayMenuImpl(menu, state).apply(menuContent)
                AppIndicator.INSTANCE.app_indicator_set_menu(indicator, menu)
                Gtk.INSTANCE.gtk_widget_show_all(menu)

                // Démarrer la boucle GTK dans un thread séparé
                Thread {
                    Gtk.INSTANCE.gtk_main()
                }.start()

                trayIcon = null
            }

            OperatingSystem.WINDOWS -> {
                WindowsTrayMenuImpl().apply(menuContent).build()

                indicator = null
                trayIcon = null
            }

            OperatingSystem.MAC -> {
                // Utiliser AWT pour les autres plateformes
                val systemTray = SystemTray.getSystemTray()
                val popupMenu = PopupMenu()

                trayIcon = TrayIcon(Toolkit.getDefaultToolkit().getImage(icon), "Custom Tray", popupMenu)
                trayIcon.isImageAutoSize = true
                AwtTrayMenuImpl(popupMenu, trayIcon).apply(menuContent)

                systemTray.add(trayIcon)

                indicator = null
            }

            OperatingSystem.UNKNOWN -> {
                // Utiliser Swing pour les autres plateformes
                val systemTray = java.awt.SystemTray.getSystemTray()
                val popupMenu = JPopupMenu()
                SwingTrayMenuImpl(popupMenu).apply(menuContent)

                // Créer une icône à partir du chemin du fichier icône
                val imageIcon = ImageIcon(icon)
                val awtImage = imageIcon.image

                trayIcon = TrayIcon(awtImage, "Custom Tray")
                trayIcon.isImageAutoSize = true

                // Ajouter un listener pour afficher le menu contextuel
                trayIcon.addActionListener {
                    // Assurer que le menu est montré en cliquant sur l'icône de la barre de tâche
                    val mouseX = MouseInfo.getPointerInfo().location.x
                    val mouseY = MouseInfo.getPointerInfo().location.y
                    popupMenu.show(null, mouseX, mouseY)
                }

                systemTray.add(trayIcon)
                indicator = null
            }
        }
    }

    private fun allocateString(str: String?): Pointer? {
        if (str == null) return null
        val charset: Charset = Charset.forName("Windows-1252")
        val byteArray = str.toByteArray(charset)
        val memory = Memory(byteArray.size.toLong() + 1) // +1 pour le terminateur null
        memory.write(0, byteArray, 0, byteArray.size)
        memory.setByte(byteArray.size.toLong(), 0.toByte()) // Terminateur null
        return memory
    }

}