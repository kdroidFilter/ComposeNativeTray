package com.kdroid.composetray.lib.linux.gtkstatusicon

import com.sun.jna.Callback
import com.sun.jna.Pointer

interface GtkStatusIconActivateCallback : Callback {
    fun invoke(status_icon: Pointer?, event_button: Int, event_time: Int)
}