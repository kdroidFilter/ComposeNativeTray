package com.kdroid.tray

import com.kdroid.lib.linux.AppIndicator
import com.kdroid.lib.linux.AppIndicatorCategory
import com.kdroid.lib.linux.AppIndicatorStatus
import com.kdroid.lib.linux.Gtk
import com.kdroid.menu.AwtTrayMenuImpl
import com.kdroid.menu.LinuxTrayMenuImpl
import com.kdroid.menu.SwingTrayMenuImpl
import com.kdroid.menu.TrayMenu
import com.kdroid.state.TrayState
import com.kdroid.utils.PlatformUtils
import com.kdroid.utils.OperatingSystem
import com.sun.jna.Pointer
import java.awt.*
import javax.swing.ImageIcon
import javax.swing.JPopupMenu

class Tray(
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

            OperatingSystem.WINDOWS, OperatingSystem.MAC -> {
                // Utiliser AWT pour les autres plateformes
                val systemTray = SystemTray.getSystemTray()
                val popupMenu = PopupMenu()
                AwtTrayMenuImpl(popupMenu).apply(menuContent)

                trayIcon = TrayIcon(Toolkit.getDefaultToolkit().getImage(icon), "Custom Tray", popupMenu)
                trayIcon.isImageAutoSize = true
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
                    val mouseX = java.awt.MouseInfo.getPointerInfo().location.x
                    val mouseY = java.awt.MouseInfo.getPointerInfo().location.y
                    popupMenu.show(null, mouseX, mouseY)
                }

                systemTray.add(trayIcon)
                indicator = null
            }
        }
    }

    fun dispose() {
        when (PlatformUtils.currentOS) {
            OperatingSystem.LINUX -> {
                // Arrêter la boucle GTK
                Gtk.INSTANCE.gtk_main_quit()
            }

            OperatingSystem.WINDOWS, OperatingSystem.MAC, OperatingSystem.UNKNOWN -> {
                // Retirer l'icône du système
                SystemTray.getSystemTray().remove(trayIcon)
            }
        }
    }
}