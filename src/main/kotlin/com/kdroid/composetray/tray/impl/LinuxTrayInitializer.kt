package com.kdroid.composetray.tray.impl

import com.kdroid.composetray.lib.linux.*
import com.kdroid.composetray.menu.api.TrayMenuBuilder
import com.kdroid.composetray.menu.impl.LinuxTrayMenuBuilderImpl
import com.sun.jna.Pointer
import com.sun.jna.Structure

object LinuxTrayInitializer {
    fun initialize(
        iconPath: String,
        primaryAction: (() -> Unit)?,
        primaryActionLinuxLabel: String,
        menuContent: TrayMenuBuilder.() -> Unit
    ) {
        // Initialiser GTK
        Gtk.INSTANCE.gtk_init(0, Pointer.createConstant(0))

        // Créer l'indicateur
        val indicator = AppIndicator.INSTANCE.app_indicator_new(
            "compose-tray", iconPath, AppIndicatorCategory.APPLICATION_STATUS
        )

        AppIndicator.INSTANCE.app_indicator_set_status(indicator, AppIndicatorStatus.ACTIVE)

        // Construire le menu
        val menu = Gtk.INSTANCE.gtk_menu_new()
        val trayMenuBuilder = LinuxTrayMenuBuilderImpl(menu)

        // Ajouter le bouton d'action principal au début de la liste sur Linux
        if (primaryAction != null) {
            trayMenuBuilder.Item(primaryActionLinuxLabel) {
                // Récupérer la position du pointeur
                val display = Gdk.INSTANCE.gdk_display_get_default()
                val seat = Gdk.INSTANCE.gdk_display_get_default_seat(display)
                val pointer = Gdk.INSTANCE.gdk_seat_get_pointer(seat)

                val x = IntArray(1)
                val y = IntArray(1)
                Gdk.INSTANCE.gdk_device_get_position(pointer, null, x, y)
                val monitor = Gdk.INSTANCE.gdk_display_get_primary_monitor(display)

                val geometry = GdkRectangle()
                Gdk.INSTANCE.gdk_monitor_get_geometry(monitor, geometry)

                val width = geometry.width
                val height = geometry.height

                println(convertPositionToCorner(x[0], y[0], width, height))
                println("Premier élément du menu cliqué à la position: x=${x[0]}, y=${y[0]}")

                // Exécuter l'action principale
                primaryAction.invoke()
            }
        }

        // Construire le menu contextuel
        trayMenuBuilder.apply(menuContent)
        AppIndicator.INSTANCE.app_indicator_set_menu(indicator, menu)
        Gtk.INSTANCE.gtk_widget_show_all(menu)

        // Démarrer la boucle principale GTK
        Gtk.INSTANCE.gtk_main()
    }
}

fun convertPositionToCorner(x: Int, y: Int, width: Int, height: Int): String {
    return when {
        x < width / 2 && y < height / 2 -> "haut gauche"
        x >= width / 2 && y < height / 2 -> "haut droite"
        x < width / 2 && y >= height / 2 -> "bas gauche"
        else -> "bas droite"
    }
}

