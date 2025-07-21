// Modified MacTrayManager.kt (add ThemeCallback and update MacTrayLibrary)
package com.kdroid.composetray.lib.mac

import androidx.compose.runtime.mutableStateOf
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.Library
import com.sun.jna.Callback
import kotlinx.coroutines.*
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal class MacTrayManager(
    private var iconPath: String,
    private var tooltip: String = "",
    onLeftClick: (() -> Unit)? = null
) {
    private val trayLib: MacTrayLibrary = Native.load("MacTray", MacTrayLibrary::class.java)
    private var tray: MacTray? = null
    private val menuItems: MutableList<MenuItem> = mutableListOf()
    private val running = AtomicBoolean(false)
    private val lock = ReentrantLock()
    private var trayThread: Thread? = null
    private val initLatch = CountDownLatch(1)

    // Coroutine scopes for callback handling
    private var mainScope: CoroutineScope? = null
    private var ioScope: CoroutineScope? = null

    // Maintain a reference to all callbacks to avoid GC
    private val callbackReferences: MutableList<Any> = mutableListOf()
    private val nativeMenuItemsReferences: MutableList<MacTrayMenuItem> = mutableListOf()
    private val onLeftClickCallback = mutableStateOf(onLeftClick)

    // Top level MenuItem class
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
    fun update(newIconPath: String, newTooltip: String, newOnLeftClick: (() -> Unit)?, newMenuItems: List<MenuItem>? = null) {
        lock.withLock {
            if (!running.get() || tray == null) return

            // Update properties
            val iconChanged = this.iconPath != newIconPath
            val tooltipChanged = this.tooltip != newTooltip
            val onLeftClickChanged = this.onLeftClickCallback.value != newOnLeftClick

            // Update icon path and tooltip
            this.iconPath = newIconPath
            this.tooltip = newTooltip
            this.onLeftClickCallback.value = newOnLeftClick

            // Update the tray object with new values
            tray?.let {
                if (iconChanged) {
                    it.icon_filepath = newIconPath
                }
                if (tooltipChanged) {
                    it.tooltip = newTooltip
                }
                if (onLeftClickChanged) {
                    initializeOnLeftClickCallback()
                }
            }

            // Update menu items if provided
            if (newMenuItems != null) {
                menuItems.clear()
                menuItems.addAll(newMenuItems)
                recreateMenu()
            } else if (iconChanged || tooltipChanged || onLeftClickChanged) {
                // If any property changed but menu items didn't, still update the tray
                trayLib.tray_update(tray!!)
            }
        }
    }

    // Recreate the menu with updated state
    private fun recreateMenu() {
        if (!running.get() || tray == null) return

        // Clear old references
        callbackReferences.clear()
        nativeMenuItemsReferences.clear()

        // Recreate the menu
        initializeTrayMenu()

        // Update the tray
        trayLib.tray_update(tray!!)
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

            // Create and start the tray thread
            trayThread = Thread {
                try {
                    // Create tray structure
                    tray = MacTray().apply {
                        icon_filepath = iconPath
                        tooltip = this@MacTrayManager.tooltip
                    }

                    initializeOnLeftClickCallback()
                    initializeTrayMenu()

                    val initResult = trayLib.tray_init(tray!!)
                    if (initResult != 0) {
                        throw IllegalStateException("Failed to initialize tray: $initResult")
                    }

                    // Signal that initialization is complete
                    initLatch.countDown()

                    // Run the event loop
                    while (running.get()) {
                        val result = trayLib.tray_loop(1)
                        if (result != 0) {
                            break
                        }
                        Thread.sleep(10)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    cleanupTray()
                }
            }.apply {
                name = "MacTray-Thread"
                isDaemon = true
                start()
            }

            // Wait for initialization to complete
            try {
                initLatch.await()
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        }
    }

    private fun initializeOnLeftClickCallback() {
        val trayObj = tray ?: return

        if (onLeftClickCallback.value != null) {
            trayObj.cb = object : TrayCallback {
                override fun invoke(tray: Pointer?) {
                    mainScope?.launch {
                        ioScope?.launch {
                            onLeftClickCallback.value?.invoke()
                        }
                    }
                }
            }
            callbackReferences.add(trayObj.cb!!)
        }
    }

    private fun initializeTrayMenu() {
        val trayObj = tray ?: return

        if (menuItems.isEmpty()) {
            return
        }

        val menuItemPrototype = MacTrayMenuItem()
        val nativeMenuItems = menuItemPrototype.toArray(menuItems.size + 1) as Array<MacTrayMenuItem>

        menuItems.forEachIndexed { index, item ->
            val nativeItem = nativeMenuItems[index]
            initializeNativeMenuItem(nativeItem, item)
            nativeItem.write()
            nativeMenuItemsReferences.add(nativeItem)
        }

        // Last element to mark the end of the menu
        nativeMenuItems[menuItems.size].text = null
        nativeMenuItems[menuItems.size].write()

        trayObj.menu = nativeMenuItems[0].pointer
    }

    private fun initializeNativeMenuItem(nativeItem: MacTrayMenuItem, menuItem: MenuItem) {
        nativeItem.text = menuItem.text
        nativeItem.disabled = if (menuItem.isEnabled) 0 else 1
        nativeItem.checked = if (menuItem.isChecked) 1 else 0

        menuItem.onClick?.let { onClick ->
            val callback = object : MenuItemCallback {
                override fun invoke(item: Pointer?) {
                    if (!running.get()) return

                    mainScope?.launch {
                        ioScope?.launch {
                            onClick()
                            // For checkable items, the onClick handler in MacTrayMenuBuilderImpl
                            // will call updateMenuItemCheckedState which will recreate the menu
                        }
                    }
                }
            }
            nativeItem.cb = callback
            callbackReferences.add(callback)
        }

        if (menuItem.subMenuItems.isNotEmpty()) {
            val subMenuPrototype = MacTrayMenuItem()
            val subMenuItemsArray = subMenuPrototype.toArray(menuItem.subMenuItems.size + 1) as Array<MacTrayMenuItem>

            menuItem.subMenuItems.forEachIndexed { index, subItem ->
                initializeNativeMenuItem(subMenuItemsArray[index], subItem)
                subMenuItemsArray[index].write()
                nativeMenuItemsReferences.add(subMenuItemsArray[index])
            }

            subMenuItemsArray[menuItem.subMenuItems.size].text = null
            subMenuItemsArray[menuItem.subMenuItems.size].write()
            nativeItem.submenu = subMenuItemsArray[0].pointer
        }
    }

    private fun cleanupTray() {
        lock.withLock {
            tray?.let {
                try {
                    trayLib.tray_exit()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // Clear all references
            callbackReferences.clear()
            nativeMenuItemsReferences.clear()
            menuItems.clear()
            tray = null
        }
    }

    fun stopTray() {
        lock.withLock {
            if (!running.get()) {
                return
            }

            running.set(false)
        }

        // Wait for the tray thread to finish
        trayThread?.let { thread ->
            try {
                thread.join(5000) // Wait up to 5 seconds
                if (thread.isAlive) {
                    thread.interrupt()
                }
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        }

        // Cancel coroutines
        mainScope?.cancel()
        ioScope?.cancel()
        mainScope = null
        ioScope = null
        trayThread = null
    }

    // Callback interfaces
    interface TrayCallback : Callback {
        fun invoke(tray: Pointer?)
    }

    interface MenuItemCallback : Callback {
        fun invoke(item: Pointer?)
    }

    interface ThemeCallback : Callback {
        fun invoke(isDark: Int)
    }

    // JNA interface for the native library
    interface MacTrayLibrary : Library {
        fun tray_init(tray: MacTray): Int
        fun tray_loop(blocking: Int): Int
        fun tray_update(tray: MacTray)
        fun tray_exit()
        fun tray_set_theme_callback(cb: ThemeCallback)
        fun tray_is_menu_dark(): Int
    }

    // Structure for a menu item
    @Structure.FieldOrder("text", "disabled", "checked", "cb", "submenu")
    class MacTrayMenuItem : Structure() {
        @JvmField var text: String? = null
        @JvmField var disabled: Int = 0
        @JvmField var checked: Int = 0
        @JvmField var cb: MenuItemCallback? = null
        @JvmField var submenu: Pointer? = null

        override fun getFieldOrder(): List<String> {
            return listOf("text", "disabled", "checked", "cb", "submenu")
        }
    }

    // Structure for the tray
    @Structure.FieldOrder("icon_filepath", "tooltip", "menu", "cb")
    class MacTray : Structure() {
        @JvmField var icon_filepath: String? = null
        @JvmField var tooltip: String? = null
        @JvmField var menu: Pointer? = null
        @JvmField var cb: TrayCallback? = null

        override fun getFieldOrder(): List<String> {
            return listOf("icon_filepath", "tooltip", "menu", "cb")
        }
    }
}