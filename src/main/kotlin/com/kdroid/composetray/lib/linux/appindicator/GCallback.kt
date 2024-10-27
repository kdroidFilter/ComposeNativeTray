package com.kdroid.composetray.lib.linux.appindicator

import com.sun.jna.Callback
import com.sun.jna.Pointer

internal interface GCallback : Callback {
    fun callback(widget: Pointer, data: Pointer?)
}
