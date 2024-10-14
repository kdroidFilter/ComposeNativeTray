package com.kdroid.composetray.tray.impl

import com.kdroid.composetray.lib.linux.AppIndicator
import com.kdroid.composetray.lib.linux.AppIndicatorCategory
import com.kdroid.composetray.lib.linux.AppIndicatorStatus
import com.kdroid.composetray.lib.linux.Gtk
import com.kdroid.composetray.menu.api.TrayMenuBuilder
import com.kdroid.composetray.menu.impl.LinuxTrayMenuBuilderImpl
import com.sun.jna.Pointer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object LinuxTrayInitializer {
    fun initialize(iconPath: String, menuContent: TrayMenuBuilder.() -> Unit) {
        // Initialize GTK
        Gtk.INSTANCE.gtk_init(0, Pointer.createConstant(0))

        // Create the indicator
        val indicator = AppIndicator.INSTANCE.app_indicator_new(
            "compose-tray",
            iconPath,
            AppIndicatorCategory.APPLICATION_STATUS
        )

        AppIndicator.INSTANCE.app_indicator_set_status(indicator, AppIndicatorStatus.ACTIVE)

        // Build the menu
        val menu = Gtk.INSTANCE.gtk_menu_new()
        val trayMenuBuilder = LinuxTrayMenuBuilderImpl(menu)
        trayMenuBuilder.apply(menuContent)
        AppIndicator.INSTANCE.app_indicator_set_menu(indicator, menu)
        Gtk.INSTANCE.gtk_widget_show_all(menu)

        Gtk.INSTANCE.gtk_main()

    }
}