package com.kdroid.composetray.tray.impl

import com.kdroid.composetray.lib.linux.appindicator.AppIndicator
import com.kdroid.composetray.lib.linux.appindicator.AppIndicatorCategory
import com.kdroid.composetray.lib.linux.appindicator.AppIndicatorStatus
import com.kdroid.composetray.lib.linux.appindicator.Gtk
import com.kdroid.composetray.lib.linux.gdk.Gdk
import com.kdroid.composetray.lib.linux.gdk.GdkRectangle
import com.kdroid.composetray.lib.linux.gtkstatusicon.GtkStatusIcon
import com.kdroid.composetray.lib.linux.gtkstatusicon.GtkStatusIconActivateCallback
import com.kdroid.composetray.menu.api.TrayMenuBuilder
import com.kdroid.composetray.menu.impl.LinuxTrayMenuBuilderImpl
import com.kdroid.composetray.utils.convertPositionToCorner
import com.kdroid.kmplog.Log
import com.kdroid.kmplog.d
import com.sun.jna.Pointer

/**
 * Object responsible for initializing and configuring the tray icon in a Linux environment.
 * Depending on the provided parameters, it can either create a tray icon with a menu or just a clickable icon.
 */
object LinuxTrayInitializer {
    fun initialize(
        iconPath: String,
        tooltip: String,
        primaryAction: (() -> Unit)?,
        primaryActionLinuxLabel: String,
        menuContent: (TrayMenuBuilder.() -> Unit)?
    ) {
        // Initialize GTK
        Gtk.INSTANCE.gtk_init(0, Pointer.createConstant(0))
        if (menuContent != null) {
            // Use AppIndicator with menu
            val indicator = AppIndicator.INSTANCE.app_indicator_new(
                "compose-tray", iconPath, AppIndicatorCategory.APPLICATION_STATUS
            )
            AppIndicator.INSTANCE.app_indicator_set_status(indicator, AppIndicatorStatus.ACTIVE)
            // Build the menu
            val menu = Gtk.INSTANCE.gtk_menu_new()
            val trayMenuBuilder = LinuxTrayMenuBuilderImpl(menu)

            // If primaryAction is provided, add a menu item for the primary action
            primaryAction?.let {
                trayMenuBuilder.Item(primaryActionLinuxLabel) {
                    // Get pointer position
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
                    Log.d("LinuxTrayInitializer", convertPositionToCorner(x[0], y[0], width, height).toString())
                    Log.d("LinuxTrayInitializer", "First menu item clicked at position: x=${x[0]}, y=${y[0]}")
                    // Execute the primary action
                    it.invoke()
                }
            }
            // Add provided menu content
            trayMenuBuilder.apply(menuContent)

            //Add a tooltip
            AppIndicator.INSTANCE.app_indicator_set_title(indicator, tooltip)

            // Attach the menu to the indicator
            AppIndicator.INSTANCE.app_indicator_set_menu(indicator, menu)
            Gtk.INSTANCE.gtk_widget_show_all(menu)
            // Start the GTK main loop
            Gtk.INSTANCE.gtk_main()
        } else if (primaryAction != null) {
            // Use GtkStatusIcon without menu
            val statusIcon = GtkStatusIcon.INSTANCE.gtk_status_icon_new_from_file(iconPath)
            // Connect the 'activate' signal to handle the click
            val callback = object : GtkStatusIconActivateCallback {
                override fun invoke(status_icon: Pointer?, event_button: Int, event_time: Int) {
                    // Execute the primary action
                    primaryAction.invoke()
                }
            }
            GtkStatusIcon.INSTANCE.g_signal_connect_data(
                statusIcon,
                "activate",
                callback,
                null,
                null,
                0
            )

            // Show the icon
            GtkStatusIcon.INSTANCE.gtk_status_icon_set_visible(statusIcon, 1)
            // Start the GTK main loop
            Gtk.INSTANCE.gtk_main()
        } else {
            // Handle the case where neither menuContent nor primaryAction are provided
            Log.d("LinuxTrayInitializer", "No menu content or primary action provided for tray icon.")
            Gtk.INSTANCE.gtk_main()
        }
    }
}