package com.kdroid.composetray.lib.linux.libtray

import com.sun.jna.Callback
import com.sun.jna.Pointer
import com.sun.jna.Structure

@Structure.FieldOrder("icon_filepath", "tooltip", "cb", "menu")
internal class LinuxNativeTray : Structure() {
    @JvmField
    var icon_filepath: String? = null

    @JvmField
    var tooltip: String? = null

    @JvmField
    var cb: TrayCallback? = null

    @JvmField
    var menu: Pointer? = null

    fun interface TrayCallback : Callback {
        fun invoke(tray: LinuxNativeTray)
    }
}