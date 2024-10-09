package com.kdroid.menu

import com.kdroid.callbacks.linux.GCallback
import com.kdroid.lib.linux.GObject
import com.kdroid.lib.linux.Gtk
import com.kdroid.state.TrayState
import com.sun.jna.Pointer

class LinuxTrayMenuImpl(private val menu: Pointer, private val state: TrayState) : TrayMenu {
    override fun Item(label: String, onClick: () -> Unit) {
        val menuItem = Gtk.INSTANCE.gtk_menu_item_new_with_label(label)
        Gtk.INSTANCE.gtk_menu_shell_append(menu, menuItem)

        val callback = object : GCallback {
            override fun callback(widget: Pointer, data: Pointer?) {
                onClick()
            }
        }

        GObject.INSTANCE.g_signal_connect_data(
            menuItem,
            "activate",
            callback,
            null,
            null,
            0
        )
    }
    override fun CheckableItem(label: String, onToggle: (Boolean) -> Unit) {
        val checkMenuItem = Gtk.INSTANCE.gtk_check_menu_item_new_with_label(label)
        Gtk.INSTANCE.gtk_menu_shell_append(menu, checkMenuItem)

        val callback = object : GCallback {
            override fun callback(widget: Pointer, data: Pointer?) {
                val active = Gtk.INSTANCE.gtk_check_menu_item_get_active(checkMenuItem)
                onToggle(active != 0)
            }
        }

        GObject.INSTANCE.g_signal_connect_data(
            checkMenuItem,
            "toggled",
            callback,
            null,
            null,
            0
        )
    }

   override fun SubMenu(label: String, submenuContent: TrayMenu.() -> Unit) {
        val menuItem = Gtk.INSTANCE.gtk_menu_item_new_with_label(label)
        val subMenu = Gtk.INSTANCE.gtk_menu_new()
        LinuxTrayMenuImpl(subMenu, state).apply(submenuContent)
        Gtk.INSTANCE.gtk_menu_item_set_submenu(menuItem, subMenu)
        Gtk.INSTANCE.gtk_menu_shell_append(menu, menuItem)
    }

   override fun Divider() {
        val separator = Gtk.INSTANCE.gtk_separator_menu_item_new()
        Gtk.INSTANCE.gtk_menu_shell_append(menu, separator)
    }
}