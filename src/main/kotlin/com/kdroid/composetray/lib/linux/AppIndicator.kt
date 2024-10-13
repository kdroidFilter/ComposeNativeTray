package com.kdroid.composetray.lib.linux

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer

internal interface AppIndicator : Library {
    companion object {
        val INSTANCE: AppIndicator = Native.load("appindicator3", AppIndicator::class.java)
    }

    fun app_indicator_new(id: String, icon_name: String, category: Int): Pointer
    fun app_indicator_set_status(indicator: Pointer, status: Int)
    fun app_indicator_set_menu(indicator: Pointer, menu: Pointer)


    fun app_indicator_set_icon_full(indicator: Pointer, icon_name: String, icon_desc: String?)
}