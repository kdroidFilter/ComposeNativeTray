package com.kdroid.composetray.lib.windows

import com.sun.jna.win32.StdCallLibrary

internal fun interface StdCallCallback : StdCallLibrary.StdCallCallback {
    fun invoke(item: WindowsNativeTrayMenuItem)
}
