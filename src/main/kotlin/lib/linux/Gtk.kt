package com.kdroid.lib.linux

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer

interface Gtk : Library {
    companion object {
        val INSTANCE: Gtk = Native.load("gtk-3", Gtk::class.java)
    }

    fun gtk_init(argc: Int, argv: Pointer)
    fun gtk_main()
    fun gtk_menu_new(): Pointer
    fun gtk_menu_item_new_with_label(label: String): Pointer
    fun gtk_menu_item_set_submenu(menu_item: Pointer, submenu: Pointer)
    fun gtk_menu_shell_append(menu_shell: Pointer, child: Pointer)
    fun gtk_widget_show_all(widget: Pointer)
    fun gtk_main_quit()
    fun gtk_separator_menu_item_new(): Pointer
    fun gtk_check_menu_item_new_with_label(label: String): Pointer
    fun gtk_check_menu_item_get_active(checkMenuItem: Pointer): Int
    fun gtk_widget_set_sensitive(widget: Pointer, sensitive: Int)

}