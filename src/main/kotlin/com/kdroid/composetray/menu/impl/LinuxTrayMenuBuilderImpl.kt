package com.kdroid.composetray.menu.impl

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kdroid.composetray.lib.linux.appindicator.GCallback
import com.kdroid.composetray.lib.linux.appindicator.GObject
import com.kdroid.composetray.lib.linux.appindicator.Gtk
import com.kdroid.composetray.menu.api.TrayMenuBuilder
import com.sun.jna.Pointer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class LinuxTrayMenuBuilderImpl(private val menu: Pointer) : TrayMenuBuilder {
    var itemClickLabel by mutableStateOf("")
        private set

    var checkableToggleState by mutableStateOf<Pair<String, Boolean>?>(null)
        private set

    private val scope = CoroutineScope(Dispatchers.Default)

    private val callbacks = mutableListOf<GCallback>()
    private val menuItems = mutableListOf<Pointer>()
    private val subMenuBuilders = mutableListOf<LinuxTrayMenuBuilderImpl>()

    // Mutex to ensure thread safety when adding callbacks and menu items
    private val mutex = Mutex()

    override fun Item(label: String, isEnabled: Boolean, onClick: () -> Unit) {
        val menuItem = Gtk.INSTANCE.gtk_menu_item_new_with_label(label)
        Gtk.INSTANCE.gtk_menu_shell_append(menu, menuItem)
        Gtk.INSTANCE.gtk_widget_set_sensitive(menuItem, if (isEnabled) 1 else 0)

        val callback = object : GCallback {
            override fun callback(widget: Pointer, data: Pointer?) {
                itemClickLabel = label
                onClick()
            }
        }

        scope.launch {
            mutex.withLock {
                // Conserver une référence au callback pour éviter la collecte par le GC
                callbacks.add(callback)
                menuItems.add(menuItem)
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
                val isActive = active != 0
                checkableToggleState = label to isActive
                onToggle(isActive)
            }
        }

        scope.launch {
            mutex.withLock {
                // Conserver une référence au callback pour éviter la collecte par le GC
                callbacks.add(callback)
                menuItems.add(checkMenuItem)
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

    override fun SubMenu(label: String, isEnabled: Boolean, submenuContent: (TrayMenuBuilder.() -> Unit)?) {
        val menuItem = Gtk.INSTANCE.gtk_menu_item_new_with_label(label)
        Gtk.INSTANCE.gtk_menu_shell_append(menu, menuItem)
        Gtk.INSTANCE.gtk_widget_set_sensitive(menuItem, if (isEnabled) 1 else 0)

        val submenu = Gtk.INSTANCE.gtk_menu_new()
        if (submenuContent == null) return
        val submenuBuilder = LinuxTrayMenuBuilderImpl(submenu).apply(submenuContent)

        // Propagation des états des sous-menus
        scope.launch {
            submenuBuilder.itemClickLabel.let { label ->
                if (label.isNotEmpty()) {
                    itemClickLabel = label
                }
            }
        }

        scope.launch {
            submenuBuilder.checkableToggleState?.let { state ->
                checkableToggleState = state
            }
        }

        scope.launch {
            mutex.withLock {
                subMenuBuilders.add(submenuBuilder)
                menuItems.add(menuItem)
            }
        }
        Gtk.INSTANCE.gtk_menu_item_set_submenu(menuItem, submenu)
    }

    override fun Divider() {
        val separator = Gtk.INSTANCE.gtk_separator_menu_item_new()
        Gtk.INSTANCE.gtk_menu_shell_append(menu, separator)
        scope.launch {
            mutex.withLock {
                menuItems.add(separator)
            }
        }
    }

    override fun dispose() {
        scope.cancel()
        Gtk.INSTANCE.gtk_main_quit()
    }
}