package com.kdroid.composetray.menu.impl

import com.kdroid.composetray.callbacks.linux.GCallback
import com.kdroid.composetray.lib.linux.GObject
import com.kdroid.composetray.lib.linux.Gtk
import com.kdroid.composetray.menu.api.TrayMenuBuilder
import com.sun.jna.Pointer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class LinuxTrayMenuBuilderImpl(private val menu: Pointer) : TrayMenuBuilder {
    private val _itemClickFlow = MutableSharedFlow<String>()
    val itemClickFlow: SharedFlow<String> get() = _itemClickFlow

    private val _checkableToggleFlow = MutableSharedFlow<Pair<String, Boolean>>()
    val checkableToggleFlow: SharedFlow<Pair<String, Boolean>> get() = _checkableToggleFlow


    override fun Item(label: String, isEnabled: Boolean, onClick: () -> Unit) {
        val menuItem = Gtk.INSTANCE.gtk_menu_item_new_with_label(label)
        Gtk.INSTANCE.gtk_menu_shell_append(menu, menuItem)
        Gtk.INSTANCE.gtk_widget_set_sensitive(menuItem, if (isEnabled) 1 else 0)

        val callback = object : GCallback {
            override fun callback(widget: Pointer, data: Pointer?) {
                CoroutineScope(Dispatchers.Default).launch {
                    _itemClickFlow.emit(label)
                }
                onClick()
            }
        }

        // Conserver une référence au callback pour éviter la collecte par le GC
        callbacks.add(callback)

        GObject.INSTANCE.g_signal_connect_data(
            menuItem,
            "activate",
            callback,
            null,
            null,
            0
        )
    }

    // Ajouter une collection pour conserver les callbacks
    private val callbacks = mutableListOf<GCallback>()


    override fun CheckableItem(label: String, isEnabled: Boolean, onToggle: (Boolean) -> Unit) {
        val checkMenuItem = Gtk.INSTANCE.gtk_check_menu_item_new_with_label(label)
        Gtk.INSTANCE.gtk_menu_shell_append(menu, checkMenuItem)
        Gtk.INSTANCE.gtk_widget_set_sensitive(checkMenuItem, if (isEnabled) 1 else 0)

        val callback = object : GCallback {
            override fun callback(widget: Pointer, data: Pointer?) {
                val active = Gtk.INSTANCE.gtk_check_menu_item_get_active(checkMenuItem)
                val isActive = active != 0
                CoroutineScope(Dispatchers.Default).launch {
                    _checkableToggleFlow.emit(label to isActive)
                }
                onToggle(isActive)
            }
        }

        // Conserver une référence au callback pour éviter la collecte par le GC
        callbacks.add(callback)

        GObject.INSTANCE.g_signal_connect_data(
            checkMenuItem,
            "toggled",
            callback,
            null,
            null,
            0
        )
    }


    override fun SubMenu(label: String, isEnabled: Boolean, submenuContent: TrayMenuBuilder.() -> Unit) {
        val menuItem = Gtk.INSTANCE.gtk_menu_item_new_with_label(label)
        Gtk.INSTANCE.gtk_menu_shell_append(menu, menuItem)
        Gtk.INSTANCE.gtk_widget_set_sensitive(menuItem, if (isEnabled) 1 else 0)

        val submenu = Gtk.INSTANCE.gtk_menu_new()
        val submenuBuilder = LinuxTrayMenuBuilderImpl(submenu).apply(submenuContent)

        // Propagation des flows des sous-menus
        CoroutineScope(Dispatchers.Default).launch {
            submenuBuilder.itemClickFlow.collect { label ->
                _itemClickFlow.emit(label)
            }
        }

        CoroutineScope(Dispatchers.Default).launch {
            submenuBuilder.checkableToggleFlow.collect { (label, state) ->
                _checkableToggleFlow.emit(label to state)
            }
        }

        subMenuBuilders.add(submenuBuilder)
        Gtk.INSTANCE.gtk_menu_item_set_submenu(menuItem, submenu)
    }

    private val subMenuBuilders = mutableListOf<LinuxTrayMenuBuilderImpl>()


    override fun Divider() {
        val separator = Gtk.INSTANCE.gtk_separator_menu_item_new()
        Gtk.INSTANCE.gtk_menu_shell_append(menu, separator)
    }

    override fun dispose() {
        Gtk.INSTANCE.gtk_main_quit()
    }

}