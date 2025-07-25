package com.kdroid.composetray.tray.impl

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.kdroid.composetray.lib.linux.SNIWrapper
import com.kdroid.composetray.tray.api.Tray
import com.sun.jna.Pointer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.awt.EventQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal class LinuxTrayManager(
    private var iconPath: String,
    private var tooltip: String = "",
    onLeftClick: (() -> Unit)? = null,
    private var primaryActionLabel: String
) {
    private val sni = SNIWrapper.INSTANCE
    private var trayHandle: Pointer? = null
    private var menuHandle: Pointer? = null
    private val menuItems: MutableList<MenuItem> = mutableListOf()
    private val running = AtomicBoolean(false)
    private val lock = ReentrantLock()
    private var trayThread: Thread? = null

    // Coroutine scopes for callback handling
    private var mainScope: CoroutineScope? = null
    private var ioScope: CoroutineScope? = null

    // Maintain references to callbacks and menu items to prevent GC
    private val callbackReferences: MutableList<Any> = mutableListOf()
    private val menuItemReferences: MutableMap<String, Pointer> = mutableMapOf() // Keyed by label for updates
    private val onLeftClickCallback = onLeftClick

    // Top level MenuItem class (similar to Mac)
    data class MenuItem(
        val text: String,
        val isEnabled: Boolean = true,
        val isCheckable: Boolean = false,
        val isChecked: Boolean = false,
        val onClick: (() -> Unit)? = null,
        val subMenuItems: List<MenuItem> = emptyList()
    )

    fun addMenuItem(menuItem: MenuItem) {
        lock.withLock {
            menuItems.add(menuItem)
        }
    }

    // Update a menu item's checked state
    fun updateMenuItemCheckedState(label: String, isChecked: Boolean) {
        lock.withLock {
            val index = menuItems.indexOfFirst { it.text == label }
            if (index != -1) {
                menuItems[index] = menuItems[index].copy(isChecked = isChecked)
                // Recreate the menu to reflect changes
                recreateMenu()
            }
        }
    }

    // Update the tray with new properties and menu items
    fun update(newIconPath: String, newTooltip: String, newOnLeftClick: (() -> Unit)?, newPrimaryActionLabel: String, newMenuItems: List<MenuItem>? = null) {
        lock.withLock {
            if (!running.get() || trayHandle == null) return

            // Update properties
            val iconChanged = this.iconPath != newIconPath
            val tooltipChanged = this.tooltip != newTooltip
            val primaryActionLabelChanged = this.primaryActionLabel != newPrimaryActionLabel

            this.iconPath = newIconPath
            this.tooltip = newTooltip
            this.primaryActionLabel = newPrimaryActionLabel

            if (iconChanged) {
                sni.update_icon_by_path(trayHandle, newIconPath)
            }
            if (tooltipChanged) {
                sni.set_tooltip_title(trayHandle, newTooltip)
                // Subtitle if needed, but example uses title only
                sni.set_tooltip_subtitle(trayHandle, "")
            }
            if (primaryActionLabelChanged || newOnLeftClick != null) {
                // Reinitialize callbacks if needed
                initializeCallbacks()
            }

            // Update menu items if provided
            if (newMenuItems != null) {
                menuItems.clear()
                menuItems.addAll(newMenuItems)
                recreateMenu()
            }
        }
    }

    // Recreate the menu with updated state
    private fun recreateMenu() {
        if (!running.get() || trayHandle == null) return

        // Destroy old menu
        menuHandle?.let { sni.destroy_menu(it) }
        menuHandle = null

        // Clear old references
        callbackReferences.clear()
        menuItemReferences.clear()

        // Recreate the menu
        initializeTrayMenu()

        // Set the new menu
        sni.set_context_menu(trayHandle, menuHandle)
    }

    // Start the tray
    fun startTray() {
        lock.withLock {
            if (running.get()) {
                return
            }

            running.set(true)

            // Create new coroutine scopes
            mainScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
            ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

            // Initialize the tray system
            val initResult = sni.init_tray_system()
            if (initResult != 0) {
                throw IllegalStateException("Failed to initialize tray system: $initResult")
            }

            // Create the tray handle
            trayHandle = sni.create_tray("composetray")
            if (trayHandle == null) {
                throw IllegalStateException("Failed to create tray")
            }

            // Set initial properties
            sni.set_title(trayHandle, "Compose Tray")
            sni.set_status(trayHandle, "Active")
            sni.set_icon_by_path(trayHandle, iconPath)
            sni.set_tooltip_title(trayHandle, tooltip)
            sni.set_tooltip_subtitle(trayHandle, "")

            // Initialize callbacks
            initializeCallbacks()

            // Initialize menu if any
            initializeTrayMenu()

            // Start the event loop in a separate thread
            trayThread = Thread {
                try {
                    sni.sni_exec() // Blocking event loop
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    cleanupTray()
                }
            }.apply {
                name = "LinuxTray-Thread"
                isDaemon = true
                start()
            }
        }
    }

    private fun initializeCallbacks() {
        trayHandle?.let { handle ->
            val activateCb = object : SNIWrapper.ActivateCallback {
                override fun invoke(x: Int, y: Int, data: Pointer?) {
                    EventQueue.invokeLater {
                        onLeftClickCallback?.invoke()
                    }
                }
            }
            sni.set_activate_callback(handle, activateCb, null)
            callbackReferences.add(activateCb)

            val secondaryCb = object : SNIWrapper.SecondaryActivateCallback {
                override fun invoke(x: Int, y: Int, data: Pointer?) {
                    // Handle secondary if desired
                }
            }
            sni.set_secondary_activate_callback(handle, secondaryCb, null)
            callbackReferences.add(secondaryCb)

            val scrollCb = object : SNIWrapper.ScrollCallback {
                override fun invoke(delta: Int, orientation: Int, data: Pointer?) {
                    // Handle scroll if desired
                }
            }
            sni.set_scroll_callback(handle, scrollCb, null)
            callbackReferences.add(scrollCb)
        }
    }

    private fun initializeTrayMenu() {
        if (menuItems.isEmpty()) return

        menuHandle = sni.create_menu()
        if (menuHandle == null) {
            throw IllegalStateException("Failed to create menu")
        }

        menuItems.forEach { item ->
            addNativeMenuItem(menuHandle!!, item)
        }
        trayHandle?.let { sni.set_context_menu(it, menuHandle) }
    }

    private fun addNativeMenuItem(parentMenu: Pointer, menuItem: MenuItem) {
        val nativeItem: Pointer? = if (menuItem.isCheckable) {
            val cb = object : SNIWrapper.ActionCallback {
                override fun invoke(data: Pointer?) {
                    if (!running.get()) return
                    EventQueue.invokeLater {
                        menuItem.onClick?.invoke()
                    }
                }
            }
            val item = sni.add_checkable_menu_action(parentMenu, menuItem.text, if (menuItem.isChecked) 1 else 0, cb, null)
            callbackReferences.add(cb)
            item
        } else if (menuItem.subMenuItems.isNotEmpty()) {
            sni.create_submenu(parentMenu, menuItem.text)
        } else if (menuItem.text == "-") {
            sni.add_menu_separator(parentMenu)
            null
        } else {
            val cb = object : SNIWrapper.ActionCallback {
                override fun invoke(data: Pointer?) {
                    if (!running.get()) return
                    EventQueue.invokeLater {
                        menuItem.onClick?.invoke()
                    }
                }
            }
            val item = if (menuItem.isEnabled) {
                sni.add_menu_action(parentMenu, menuItem.text, cb, null)
            } else {
                sni.add_disabled_menu_action(parentMenu, menuItem.text, cb, null)
            }
            callbackReferences.add(cb)
            item
        }

        nativeItem?.let {
            menuItemReferences[menuItem.text] = it
        }

        if (menuItem.subMenuItems.isNotEmpty() && nativeItem != null) {
            menuItem.subMenuItems.forEach { subItem ->
                addNativeMenuItem(nativeItem, subItem)
            }
        }
    }

    private fun cleanupTray() {
        lock.withLock {
            trayHandle?.let {
                menuHandle?.let { mh -> sni.destroy_menu(mh) }
                sni.destroy_handle(it)
                sni.shutdown_tray_system()
            }

            // Clear all references
            callbackReferences.clear()
            menuItemReferences.clear()
            menuItems.clear()
            trayHandle = null
            menuHandle = null
        }
    }

    fun stopTray() {
        lock.withLock {
            if (!running.get()) {
                return
            }

            running.set(false)
        }

        // Interrupt the event loop if necessary; but sni_exec is blocking, so we may need to handle shutdown
        // For SNI, shutdown_tray_system should be called, but since it's in the thread, it will cleanup on exit

        trayThread?.let { thread ->
            try {
                thread.interrupt() // If needed to break the loop
                thread.join(5000) // Wait up to 5 seconds
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        }

        running.set(false)
        sni.sni_stop_exec()
        trayThread?.join(5000)
        trayThread = null
    }
}

