package com.kdroid.composetray.callbacks.windows

import WindowsNativeTrayMenuItem
import com.sun.jna.win32.StdCallLibrary

fun interface MenuItemCallback : StdCallLibrary.StdCallCallback {
    fun invoke(item: WindowsNativeTrayMenuItem)
}