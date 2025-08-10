package com.kdroid.composetray.lib.windows

import com.sun.jna.Pointer
import com.sun.jna.Structure

@Structure.FieldOrder("text", "icon_path", "disabled", "checked", "cb", "submenu")
internal open class WindowsNativeTrayMenuItem : Structure() {
    @JvmField
    var text: String? = null

    @JvmField
    var icon_path: String? = null

    @JvmField
    var disabled: Int = 0

    @JvmField
    var checked: Int = 0

    @JvmField
    var cb: StdCallCallback? = null

    @JvmField
    var submenu: Pointer? = null
}