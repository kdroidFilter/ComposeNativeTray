package com.kdroid.composetray.lib.windows

import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.win32.StdCallLibrary

@Structure.FieldOrder("icon_filepath", "tooltip", "cb", "menu")
internal class WindowsNativeTray : Structure() {
    companion object {
        init {
            // Ensure UTF-8 encoding for JNA string marshaling before any structure write
            val key = "jna.encoding"
            if (System.getProperty(key).isNullOrBlank()) {
                System.setProperty(key, "UTF-8")
            }
        }
    }

    @JvmField
    var icon_filepath: String? = null

    @JvmField
    var tooltip: String? = null

    @JvmField
    var cb: TrayCallback? = null

    @JvmField
    var menu: Pointer? = null

    fun interface TrayCallback : StdCallLibrary.StdCallCallback {
        fun invoke(tray: WindowsNativeTray)
    }
}
