package com.kdroid.composetray.lib.windows

import com.sun.jna.Native
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean

internal class WindowsTrayManager(
    private var iconPath: String,
    private var tooltip: String = "",
    private var onLeftClick: (() -> Unit)? = null
) {
    private val trayLib: WindowsNativeTrayLibrary = Native.load("tray", WindowsNativeTrayLibrary::class.java)
    private val tray: WindowsNativeTray = WindowsNativeTray()
    private val running = AtomicBoolean(false)
    private val initialized = AtomicBoolean(false)

    // Maintain a reference to all callbacks to avoid GC
    private val callbackReferences: MutableList<StdCallCallback> = mutableListOf()
    private val nativeMenuItemsReferences: MutableList<WindowsNativeTrayMenuItem> = mutableListOf()

    // Keep a reference to the tray callback
    private var trayCallback: WindowsNativeTray.TrayCallback? = null

    // Coroutine for running the tray loop
    private var trayJob: Job? = null
    private val trayScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        tray.icon_filepath = iconPath
        tray.tooltip = tooltip
    }

    // Top level MenuItem class
    data class MenuItem(
        val text: String,
        val isEnabled: Boolean = true,
        val isCheckable: Boolean = false,
        val isChecked: Boolean = false,
        val onClick: (() -> Unit)? = null,
        val subMenuItems: List<MenuItem> = emptyList()
    )

    fun initialize(menuItems: List<MenuItem>) {
        if (initialized.get()) {
            return
        }

        synchronized(tray) {
            // Set up the left click callback
            setupLeftClickCallback()

            // Set up the menu
            setupMenu(menuItems)

            // Initialize the tray
            val initResult = trayLib.tray_init(tray)
            if (initResult != 0) {
                throw RuntimeException("Failed to initialize tray: $initResult")
            }

            initialized.set(true)
            running.set(true)

            // Start the message loop
            startMessageLoop()
        }
    }

    fun update(newIconPath: String, newTooltip: String, newOnLeftClick: (() -> Unit)?, newMenuItems: List<MenuItem>) {
        if (!initialized.get()) {
            // If not initialized, store the values and initialize
            iconPath = newIconPath
            tooltip = newTooltip
            onLeftClick = newOnLeftClick
            tray.icon_filepath = newIconPath
            tray.tooltip = newTooltip
            initialize(newMenuItems)
            return
        }

        synchronized(tray) {
            // Update properties
            iconPath = newIconPath
            tooltip = newTooltip
            onLeftClick = newOnLeftClick

            // Update tray structure
            tray.icon_filepath = newIconPath
            tray.tooltip = newTooltip

            // Update left click callback
            setupLeftClickCallback()

            // Clear old references
            callbackReferences.clear()
            nativeMenuItemsReferences.clear()

            // Set up new menu
            setupMenu(newMenuItems)

            // Update the native tray
            trayLib.tray_update(tray)
        }
    }

    private fun setupLeftClickCallback() {
        trayCallback = if (onLeftClick != null) {
            WindowsNativeTray.TrayCallback {
                onLeftClick?.invoke()
            }
        } else {
            null
        }
        tray.cb = trayCallback
    }

    private fun setupMenu(menuItems: List<MenuItem>) {
        if (menuItems.isEmpty()) {
            tray.menu = null
            return
        }

        val menuItemPrototype = WindowsNativeTrayMenuItem()
        val nativeMenuItems = menuItemPrototype.toArray(menuItems.size + 1) as Array<WindowsNativeTrayMenuItem>

        menuItems.forEachIndexed { index, item ->
            val nativeItem = nativeMenuItems[index]
            initializeNativeMenuItem(nativeItem, item)
            nativeItem.write()
            nativeMenuItemsReferences.add(nativeItem)
        }

        // Last element to mark the end of the menu
        nativeMenuItems[menuItems.size].text = null
        nativeMenuItems[menuItems.size].write()

        tray.menu = nativeMenuItems[0].pointer
    }

    private fun initializeNativeMenuItem(nativeItem: WindowsNativeTrayMenuItem, menuItem: MenuItem) {
        nativeItem.text = menuItem.text
        nativeItem.disabled = if (menuItem.isEnabled) 0 else 1
        nativeItem.checked = if (menuItem.isChecked) 1 else 0

        // Create the menu item callback
        menuItem.onClick?.let { onClick ->
            val callback = StdCallCallback { item ->
                if (running.get()) {
                    onClick()
                    if (menuItem.isCheckable) {
                        item.checked = if (item.checked == 0) 1 else 0
                        item.write()
                        trayLib.tray_update(tray)
                    }
                }
            }
            nativeItem.cb = callback
            callbackReferences.add(callback)
        }

        // Handle submenus
        if (menuItem.subMenuItems.isNotEmpty()) {
            val subMenuPrototype = WindowsNativeTrayMenuItem()
            val subMenuItemsArray = subMenuPrototype.toArray(menuItem.subMenuItems.size + 1) as Array<WindowsNativeTrayMenuItem>

            menuItem.subMenuItems.forEachIndexed { index, subItem ->
                initializeNativeMenuItem(subMenuItemsArray[index], subItem)
                subMenuItemsArray[index].write()
                nativeMenuItemsReferences.add(subMenuItemsArray[index])
            }

            // End marker
            subMenuItemsArray[menuItem.subMenuItems.size].text = null
            subMenuItemsArray[menuItem.subMenuItems.size].write()
            nativeItem.submenu = subMenuItemsArray[0].pointer
        }
    }

    private fun startMessageLoop() {
        trayJob = trayScope.launch {
            while (running.get() && isActive) {
                val result = trayLib.tray_loop(0) // Non-blocking
                if (result != 0) break
                delay(10) // Small delay to avoid CPU spinning
            }
        }
    }

    fun stopTray() {
        running.set(false)

        // Cancel the coroutine
        trayJob?.cancel()

        if (initialized.get()) {
            trayLib.tray_exit()
            initialized.set(false)
        }

        // Clear all references
        callbackReferences.clear()
        nativeMenuItemsReferences.clear()
        trayCallback = null
    }
}