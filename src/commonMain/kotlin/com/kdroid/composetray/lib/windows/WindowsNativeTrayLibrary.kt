package com.kdroid.composetray.lib.windows

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import com.sun.jna.win32.StdCallLibrary

// Use JNA direct mapping instead of interface mapping.
// This object registers the native library and exposes external (native) methods.
internal object WindowsNativeTrayLibrary : StdCallLibrary {
    init {
        // Register the native library "tray" for direct calls
        Native.register("tray")
    }

    @JvmStatic external fun tray_get_instance(): Pointer?
    @JvmStatic external fun tray_init(tray: WindowsNativeTray): Int
    @JvmStatic external fun tray_loop(blocking: Int): Int
    @JvmStatic external fun tray_update(tray: WindowsNativeTray)
    @JvmStatic external fun tray_exit()
    @JvmStatic external fun tray_get_notification_icons_position(x: IntByReference, y: IntByReference) : Int
    @JvmStatic external fun tray_get_notification_icons_region(): String?
}
