package com.kdroid.composetray.menu

import com.kdroid.composetray.callbacks.linux.GCallback
import com.kdroid.composetray.lib.linux.GObject
import com.kdroid.composetray.lib.linux.Gtk
import com.sun.jna.Pointer

class LinuxTrayMenuImpl(private val menu: Pointer) : TrayMenu {
    override fun Item(label: String, isEnabled: Boolean, onClick: () -> Unit) {
        val menuItem = Gtk.INSTANCE.gtk_menu_item_new_with_label(label)
        Gtk.INSTANCE.gtk_menu_shell_append(menu, menuItem)
        Gtk.INSTANCE.gtk_widget_set_sensitive(menuItem, if (isEnabled) 1 else 0)

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

    override fun CheckableItem(label: String, isEnabled: Boolean, onToggle: (Boolean) -> Unit) {
        val checkMenuItem = Gtk.INSTANCE.gtk_check_menu_item_new_with_label(label)
        Gtk.INSTANCE.gtk_menu_shell_append(menu, checkMenuItem)
        Gtk.INSTANCE.gtk_widget_set_sensitive(checkMenuItem, if (isEnabled) 1 else 0)

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

    override fun SubMenu(label: String, isEnabled: Boolean, submenuContent: TrayMenu.() -> Unit) {
        val menuItem = Gtk.INSTANCE.gtk_menu_item_new_with_label(label)
        Gtk.INSTANCE.gtk_widget_set_sensitive(menuItem, if (isEnabled) 1 else 0)

        val subMenu = Gtk.INSTANCE.gtk_menu_new()
        LinuxTrayMenuImpl(subMenu).apply(submenuContent)
        Gtk.INSTANCE.gtk_menu_item_set_submenu(menuItem, subMenu)
        Gtk.INSTANCE.gtk_menu_shell_append(menu, menuItem)
    }

    override fun Divider() {
        val separator = Gtk.INSTANCE.gtk_separator_menu_item_new()
        Gtk.INSTANCE.gtk_menu_shell_append(menu, separator)
    }

    override fun dispose() {
        Gtk.INSTANCE.gtk_main_quit()
    }
}
