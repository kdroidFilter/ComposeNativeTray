package com.kdroid.composetray.lib.linux

import com.sun.jna.*

// JNA Interface for sni_wrapper library
interface SNIWrapper : Library {
    companion object {
        // Load the shared library (adjust the path and name as needed)
        val INSTANCE: SNIWrapper = Native.load("tray", SNIWrapper::class.java) as SNIWrapper
    }

    // Callback interfaces
    interface ActivateCallback : Callback {
        fun invoke(x: Int, y: Int, data: Pointer?)
    }

    interface SecondaryActivateCallback : Callback {
        fun invoke(x: Int, y: Int, data: Pointer?)
    }

    interface ScrollCallback : Callback {
        fun invoke(delta: Int, orientation: Int, data: Pointer?)
    }

    interface ActionCallback : Callback {
        fun invoke(data: Pointer?)
    }

    // System tray initialization and cleanup
    fun init_tray_system(): Int
    fun shutdown_tray_system()

    // Tray creation and destruction
    fun create_tray(id: String?): Pointer?
    fun destroy_handle(handle: Pointer?)

    // Tray property setters
    fun set_title(handle: Pointer?, title: String?)
    fun set_status(handle: Pointer?, status: String?)
    fun set_icon_by_name(handle: Pointer?, name: String?)
    fun set_icon_by_path(handle: Pointer?, path: String?)
    fun update_icon_by_path(handle: Pointer?, path: String?)
    fun set_tooltip_title(handle: Pointer?, title: String?)
    fun set_tooltip_subtitle(handle: Pointer?, subTitle: String?)

    // Menu creation and management
    fun create_menu(): Pointer?
    fun destroy_menu(menu_handle: Pointer?)
    fun set_context_menu(handle: Pointer?, menu: Pointer?)
    fun add_menu_action(menu_handle: Pointer?, text: String?, cb: ActionCallback?, data: Pointer?): Pointer?
    fun add_disabled_menu_action(menu_handle: Pointer?, text: String?, cb: ActionCallback?, data: Pointer?): Pointer?
    fun add_checkable_menu_action(menu_handle: Pointer?, text: String?, checked: Int, cb: ActionCallback?, data: Pointer?): Pointer?    fun add_menu_separator(menu_handle: Pointer?)
    fun create_submenu(menu_handle: Pointer?, text: String?): Pointer?
    fun set_menu_item_text(menu_item_handle: Pointer?, text: String?)
    fun set_menu_item_enabled(menu_item_handle: Pointer?, enabled: Int)
    fun set_menu_item_checked(menu_item_handle: Pointer?, checked: Int): Int
    fun remove_menu_item(menu_handle: Pointer?, menu_item_handle: Pointer?)
    fun set_menu_item_icon(menu_item_handle: Pointer?, icon_path_or_name: String?)
    fun tray_update(handle: Pointer?)

    fun clear_menu(menu_handle: Pointer?)

    // Tray event callbacks
    fun set_activate_callback(handle: Pointer?, cb: ActivateCallback?, data: Pointer?)
    fun set_secondary_activate_callback(handle: Pointer?, cb: SecondaryActivateCallback?, data: Pointer?)
    fun set_scroll_callback(handle: Pointer?, cb: ScrollCallback?, data: Pointer?)

    // Notifications
    fun show_notification(handle: Pointer?, title: String?, msg: String?, iconName: String?, secs: Int)

    // Event loop management
    fun sni_exec(): Int
    fun sni_process_events()
    fun sni_stop_exec()

    //Debug mode management
    fun sni_set_debug_mode(enabled: Int)
}
