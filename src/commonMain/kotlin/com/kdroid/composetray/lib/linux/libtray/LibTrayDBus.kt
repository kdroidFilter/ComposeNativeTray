package com.kdroid.composetray.lib.linux.libtray

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer

/**
 * JNA interface for the linuxlibdbus native library.
 * This library uses DBus for system tray integration, which fixes issues with GNOME.
 */
internal interface LibTrayDBus : Library {
    companion object {
        val INSTANCE: LibTrayDBus = Native.load("tray", LibTrayDBus::class.java)
    }

    // System tray initialization and cleanup
    fun init_tray_system(): Int
    fun shutdown_tray_system()

    // Tray creation and destruction
    fun create_tray(id: String): Pointer?
    fun destroy_handle(handle: Pointer)

    // Tray property setters
    fun set_title(handle: Pointer, title: String)
    fun set_status(handle: Pointer, status: String)
    fun set_icon_by_name(handle: Pointer, name: String)
    fun set_icon_by_path(handle: Pointer, path: String)
    fun update_icon_by_path(handle: Pointer, path: String)
    fun set_tooltip_title(handle: Pointer, title: String)
    fun set_tooltip_subtitle(handle: Pointer, subTitle: String)

    // Menu creation and management
    fun create_menu(): Pointer?
    fun set_context_menu(handle: Pointer, menu: Pointer)
    fun add_menu_action(menuHandle: Pointer, text: String, cb: ActionCallback, data: Pointer?): Pointer?
    fun add_disabled_menu_action(menuHandle: Pointer, text: String, cb: ActionCallback, data: Pointer?): Pointer?
    fun add_checkable_menu_action(menuHandle: Pointer, text: String, checked: Int, cb: ActionCallback, data: Pointer?)
    fun add_menu_separator(menuHandle: Pointer)
    fun create_submenu(menuHandle: Pointer, text: String): Pointer?
    fun set_menu_item_text(menuItemHandle: Pointer, text: String)
    fun set_menu_item_enabled(menuItemHandle: Pointer, enabled: Int)
    fun remove_menu_item(menuHandle: Pointer, menuItemHandle: Pointer)

    // Tray event callbacks
    fun set_activate_callback(handle: Pointer, cb: ActivateCallback, data: Pointer?)
    fun set_secondary_activate_callback(handle: Pointer, cb: SecondaryActivateCallback, data: Pointer?)
    fun set_scroll_callback(handle: Pointer, cb: ScrollCallback, data: Pointer?)

    // Notifications
    fun show_notification(handle: Pointer, title: String, msg: String, iconName: String, secs: Int)

    // Event loop management
    fun sni_exec(): Int
    fun sni_process_events()

    // Callback interfaces
    interface ActionCallback : com.sun.jna.Callback {
        fun invoke(userData: Pointer?)
    }

    interface ActivateCallback : com.sun.jna.Callback {
        fun invoke(x: Int, y: Int, userData: Pointer?)
    }

    interface SecondaryActivateCallback : com.sun.jna.Callback {
        fun invoke(x: Int, y: Int, userData: Pointer?)
    }

    interface ScrollCallback : com.sun.jna.Callback {
        fun invoke(delta: Int, orientation: Int, userData: Pointer?)
    }
}