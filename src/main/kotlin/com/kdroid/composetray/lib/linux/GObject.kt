package com.kdroid.composetray.lib.linux

import com.sun.jna.Callback
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer

internal interface GObject : Library {
    companion object {
        val INSTANCE: GObject = Native.load("gobject-2.0", GObject::class.java)
    }

    fun g_signal_connect_data(
        instance: Pointer,
        detailed_signal: String,
        c_handler: Callback,
        data: Pointer?,
        destroy_data: Pointer?,
        connect_flags: Int
    ): Long
}