package com.kdroid.composetray.menu.impl

import com.kdroid.composetray.lib.linux.libtray.LibTrayDBus
import com.kdroid.composetray.menu.api.TrayMenuBuilder
import com.kdroid.composetray.utils.debugln
import com.sun.jna.Pointer
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Implementation of TrayMenuBuilder for Linux using the DBus-based tray library.
 * This implementation fixes issues with GNOME desktop environments.
 */
internal class LinuxDBusTrayMenuBuilderImpl(
    private val iconPath: String,
    private val tooltip: String = "",
    private val onLeftClick: (() -> Unit)?
) : TrayMenuBuilder {

    private val trayLib: LibTrayDBus = LibTrayDBus.INSTANCE
    private val lock = ReentrantLock()
    
    // Menu handle from the native library
    private var menuHandle: Pointer? = null
    
    // Keep track of menu items to prevent garbage collection of callbacks
    private val menuItems = mutableMapOf<String, Pointer?>()
    private val persistentCallbacks = mutableListOf<LibTrayDBus.ActionCallback>()
    
    init {
        // Create the menu when the builder is instantiated
        lock.withLock {
            menuHandle = trayLib.create_menu()
        }
    }

    override fun Item(label: String, isEnabled: Boolean, onClick: () -> Unit) {
        lock.withLock {
            if (menuHandle == null) return
            
            val callback = object : LibTrayDBus.ActionCallback {
                override fun invoke(userData: Pointer?) {
                    onClick()
                }
            }
            persistentCallbacks.add(callback)
            
            val itemHandle = if (isEnabled) {
                trayLib.add_menu_action(menuHandle!!, label, callback, null)
            } else {
                trayLib.add_disabled_menu_action(menuHandle!!, label, callback, null)
            }
            
            menuItems[label] = itemHandle
        }
    }

    override fun CheckableItem(
        label: String,
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit,
        isEnabled: Boolean
    ) {
        lock.withLock {
            if (menuHandle == null) return
            
            val callback = object : LibTrayDBus.ActionCallback {
                override fun invoke(userData: Pointer?) {
                    // The native library toggles the checked state internally
                    onCheckedChange(!checked)
                }
            }
            persistentCallbacks.add(callback)
            
            trayLib.add_checkable_menu_action(
                menuHandle!!,
                label,
                if (checked) 1 else 0,
                callback,
                null
            )
        }
    }

    override fun SubMenu(
        label: String,
        isEnabled: Boolean,
        submenuContent: (TrayMenuBuilder.() -> Unit)?
    ) {
        if (submenuContent == null) return
        
        lock.withLock {
            if (menuHandle == null) return
            
            // Create a submenu
            val submenuHandle = trayLib.create_submenu(menuHandle!!, label)
            if (submenuHandle == null) {
                debugln { "Failed to create submenu: $label" }
                return
            }
            
            menuItems[label] = submenuHandle
            
            // Create a new builder for the submenu
            val subMenuBuilder = LinuxDBusTrayMenuBuilderImpl(iconPath, tooltip, onLeftClick)
            
            // Set the submenu handle in the builder
            subMenuBuilder.menuHandle = submenuHandle
            
            // Build the submenu content
            submenuContent.invoke(subMenuBuilder)
            
            // Disable the submenu if needed
            if (!isEnabled) {
                trayLib.set_menu_item_enabled(submenuHandle, 0)
            }
        }
    }

    override fun Divider() {
        lock.withLock {
            if (menuHandle == null) return
            trayLib.add_menu_separator(menuHandle!!)
        }
    }

    override fun dispose() {
        lock.withLock {
            // Clear references to prevent memory leaks
            persistentCallbacks.clear()
            menuItems.clear()
            // Note: We don't destroy the menu handle here as it's owned by the tray
            menuHandle = null
        }
    }

    /**
     * Returns the menu handle to be used with the tray.
     */
    fun getMenuHandle(): Pointer? = lock.withLock {
        menuHandle
    }
}