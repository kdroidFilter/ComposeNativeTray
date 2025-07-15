package com.kdroid.composetray.lib.linux.libtray

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer

internal interface LibTray : Library {
    companion object {
        val INSTANCE: LibTray = Native.load("tray", LibTray::class.java)
    }

    fun tray_get_instance(): Pointer?
    fun tray_init(tray: LinuxNativeTray): Int
    fun tray_loop(blocking: Int): Int
    fun tray_update(tray: LinuxNativeTray)
    fun tray_exit()
}