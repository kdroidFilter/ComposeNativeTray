package com.kdroid.composetray.lib.linux.gtkstatusicon

import com.sun.jna.Callback
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer

interface GtkStatusIcon : Library {
    companion object {
        val INSTANCE: GtkStatusIcon = Native.load("gtk-3", GtkStatusIcon::class.java)
    }

    fun gtk_status_icon_new_from_file(filename: String): Pointer
    fun gtk_status_icon_set_visible(status_icon: Pointer, visible: Int)
    fun gtk_status_icon_set_tooltip_text(status_icon: Pointer, tooltip: String)
    fun gtk_status_icon_as_widget(status_icon: Pointer): Pointer

    // GObject signal connection
    fun g_signal_connect_data(
        instance: Pointer,
        detailed_signal: String,
        c_handler: Callback,
        data: Pointer?,
        destroy_data: Pointer?,
        flags: Int
    ): Pointer

     fun g_signal_handlers_disconnect_matched(statusIcon: Pointer, i: Int, i1: Int, nothing: Nothing?, nothing1: Nothing?, nothing2: Nothing?, nothing3: Nothing?)
}