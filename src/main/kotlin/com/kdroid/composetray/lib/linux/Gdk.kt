package com.kdroid.composetray.lib.linux

import com.kdroid.composetray.tray.impl.GdkRectangle
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer


internal interface Gdk : Library {
    companion object {
        val INSTANCE: Gdk = Native.load("gdk-3", Gdk::class.java)
    }

    fun gdk_display_get_default(): Pointer?
    fun gdk_display_get_default_seat(display: Pointer?): Pointer?
    fun gdk_seat_get_pointer(seat: Pointer?): Pointer?
    fun gdk_device_get_position(device: Pointer?, screen: Pointer?, x: IntArray, y: IntArray)
    fun gdk_display_get_primary_monitor(display: Pointer?): Pointer?
    fun gdk_monitor_get_geometry(monitor: Pointer?, geometry: GdkRectangle)
}
