package com.kdroid.callbacks.linux

import com.sun.jna.Callback
import com.sun.jna.Pointer

interface GCallback : Callback {
    fun callback(widget: Pointer, data: Pointer?)
}
