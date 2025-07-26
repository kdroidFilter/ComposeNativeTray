package com.kdroid.composetray.lib.linux

import com.sun.jna.Pointer
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal class LinuxTrayManager(
    private var iconPath: String,
    private var tooltip: String = "",
    private var onLeftClick: (() -> Unit)? = null,
    private var primaryActionLabel: String
) {
    private val sni = SNIWrapper.INSTANCE
    private var trayHandle: Pointer? = null
    private var menuHandle: Pointer? = null
    private val menuItems: MutableList<MenuItem> = mutableListOf()
    private val running = AtomicBoolean(false)
    private val lock = ReentrantLock()
    private var trayThread: Thread? = null
    private val initLatch = CountDownLatch(1)
    private var shutdownHook: Thread? = null

    // Maintain references to callbacks and menu items to prevent GC
    private val callbackReferences: MutableList<Any> = mutableListOf()
    private val menuItemReferences: MutableMap<String, Pointer> = mutableMapOf()

    // Store callbacks as instance variables to prevent GC
    private var activateCallback: SNIWrapper.ActivateCallback? = null
    private var secondaryActivateCallback: SNIWrapper.SecondaryActivateCallback? = null
    private var scrollCallback: SNIWrapper.ScrollCallback? = null

    private val taskQueue = java.util.concurrent.ConcurrentLinkedQueue<() -> Unit>()
    private fun runOnTrayThread(action: () -> Unit) {
        taskQueue += action
    }

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

    private fun schedule(action: () -> Unit) {
        taskQueue += action        // petite aide utilitaire
    }

    fun updateMenuItemCheckedState(label: String, isChecked: Boolean) {
        var needRefresh = false
        lock.withLock {
            val idx = menuItems.indexOfFirst { it.text == label }
            if (idx != -1) {
                menuItems[idx] = menuItems[idx].copy(isChecked = isChecked)
                needRefresh = true
            }
        }
        if (needRefresh) {
            schedule { recreateMenu() }   // ← exécuté dans la boucle tray
        }
    }


    fun update(
        newIconPath: String,
        newTooltip: String,
        newOnLeftClick: (() -> Unit)?,
        newPrimaryActionLabel: String,
        newMenuItems: List<MenuItem>? = null
    ) {
        var iconChanged: Boolean
        var tooltipChanged: Boolean
        var needsMenuRefresh = false

        lock.withLock {
            if (!running.get() || trayHandle == null) return

            iconChanged = iconPath != newIconPath
            tooltipChanged = tooltip != newTooltip

            iconPath = newIconPath
            tooltip = newTooltip
            onLeftClick = newOnLeftClick
            primaryActionLabel = newPrimaryActionLabel

            if (newMenuItems != null) {
                menuItems.clear()
                menuItems.addAll(newMenuItems)
                needsMenuRefresh = true
            }
        }

        if (iconChanged)   sni.update_icon_by_path(trayHandle, newIconPath)
        if (tooltipChanged) sni.set_tooltip_title(trayHandle, newTooltip)

        if (needsMenuRefresh) {
            runOnTrayThread { recreateMenu() }
        }
    }

    private fun recreateMenu() {
        println("LinuxTrayManager: Recreating menu")
        if (!running.get() || trayHandle == null) {
            println("LinuxTrayManager: Cannot recreate menu, tray is not running or trayHandle is null")
            return
        }

        try {
            // Clear callback references
            callbackReferences.clear()
            menuItemReferences.clear()

            // If we have menu items to show
            if (menuItems.isNotEmpty()) {
                // Create menu if it doesn't exist
                if (menuHandle == null) {
                    menuHandle = sni.create_menu()
                    if (menuHandle == null) {
                        println("LinuxTrayManager: Failed to create menu")
                        return
                    }
                    println("LinuxTrayManager: Menu created")
                } else {
                    // Clear existing menu instead of destroying it
                    try {
                        sni.clear_menu(menuHandle)
                        println("LinuxTrayManager: Menu cleared")
                    } catch (e: Exception) {
                        // If clear_menu is not available, fall back to recreating
                        println("LinuxTrayManager: clear_menu not available, recreating menu")
                        val oldMenu = menuHandle
                        menuHandle = sni.create_menu()
                        if (menuHandle != null) {
                            sni.set_context_menu(trayHandle, null)
                            Thread.sleep(50)
                            sni.destroy_menu(oldMenu!!)
                        } else {
                            menuHandle = oldMenu
                            return
                        }
                    }
                }

                // Add all menu items
                menuItems.forEach { item ->
                    addNativeMenuItem(menuHandle!!, item)
                }

                // Set the menu on the tray
                sni.set_context_menu(trayHandle, menuHandle)
                println("LinuxTrayManager: Context menu set")

            } else {
                // No menu items, remove the menu
                if (menuHandle != null) {
                    sni.set_context_menu(trayHandle, null)
                    Thread.sleep(50) // Wait for DBus to process
                    sni.destroy_menu(menuHandle!!)
                    menuHandle = null
                    println("LinuxTrayManager: Menu removed")
                }
            }

            // Force update if available
            try {
                sni.tray_update(trayHandle)
                println("LinuxTrayManager: Tray update forced")
            } catch (e: Exception) {
                // tray_update might not be available
                println("LinuxTrayManager: tray_update not available: ${e.message}")
            }

        } catch (e: Exception) {
            println("LinuxTrayManager: Error recreating menu: ${e.message}")
            e.printStackTrace()
        }
    }

    fun startTray() {
        lock.withLock {
            if (running.get()) return
            running.set(true)
        }

        // Add a shutdown hook for cleanup on JVM exit
        shutdownHook = Thread {
            stopTray()
        }.also {
            Runtime.getRuntime().addShutdownHook(it)
        }

        // Start the event loop in a separate thread
        trayThread = Thread {
            try {
                // Initialize the tray system in the same thread as the event loop
                val initResult = sni.init_tray_system()
                if (initResult != 0) {
                    throw IllegalStateException("Failed to initialize tray system: $initResult")
                }

                // Create the tray handle
                trayHandle = sni.create_tray("composetray-${System.currentTimeMillis()}")
                if (trayHandle == null) {
                    sni.shutdown_tray_system()
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

                // Signal that initialization is complete
                initLatch.countDown()

                while (running.get()) {
                    sni.sni_process_events()     // traite une itération GLib non bloquante
                    while (true) taskQueue.poll()?.invoke() ?: break
                    Thread.sleep(50)            // petit yield
                }
            } catch (e: InterruptedException) {
                // Ignore interrupted exception during shutdown (expected behavior)
                Thread.currentThread().interrupt()  // Restore interrupt flag for proper handling elsewhere
            } catch (e: Exception) {
                e.printStackTrace()  // Print only for unexpected exceptions
            } finally {
                cleanupTray()
            }
        }.apply {
            name = "LinuxTray-Thread"
            isDaemon = true
            start()
        }

        // Wait for initialization to complete
        try {
            initLatch.await()
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    private fun initializeCallbacks() {
        trayHandle?.let { handle ->
            // Create and store callbacks as instance variables
            activateCallback = object : SNIWrapper.ActivateCallback {
                override fun invoke(x: Int, y: Int, data: Pointer?) {
                    onLeftClick?.invoke()
                }
            }
            sni.set_activate_callback(handle, activateCallback, null)

            secondaryActivateCallback = object : SNIWrapper.SecondaryActivateCallback {
                override fun invoke(x: Int, y: Int, data: Pointer?) {
                    // Handle secondary click if needed
                }
            }
            sni.set_secondary_activate_callback(handle, secondaryActivateCallback, null)

            scrollCallback = object : SNIWrapper.ScrollCallback {
                override fun invoke(delta: Int, orientation: Int, data: Pointer?) {
                    // Handle scroll if needed
                }
            }
            sni.set_scroll_callback(handle, scrollCallback, null)
        }
    }

    private fun initializeTrayMenu() {
        if (menuItems.isEmpty()) return

        menuHandle = sni.create_menu()
        if (menuHandle == null) {
            println("Failed to create menu")
            return
        }

        menuItems.forEach { item ->
            addNativeMenuItem(menuHandle!!, item)
        }

        trayHandle?.let {
            sni.set_context_menu(it, menuHandle)
        }
    }

    private fun addNativeMenuItem(parentMenu: Pointer, menuItem: MenuItem) {
        when {
            menuItem.text == "-" -> {
                sni.add_menu_separator(parentMenu)
            }

            menuItem.subMenuItems.isNotEmpty() -> {
                val submenu = sni.create_submenu(parentMenu, menuItem.text)
                if (submenu != null) {
                    menuItemReferences[menuItem.text] = submenu
                    menuItem.subMenuItems.forEach { subItem ->
                        addNativeMenuItem(submenu, subItem)
                    }
                }
            }

            menuItem.isCheckable -> {
                val cb = createActionCallback(menuItem)
                val item = sni.add_checkable_menu_action(
                    parentMenu,
                    menuItem.text,
                    if (menuItem.isChecked) 1 else 0,
                    cb,
                    null
                )
                callbackReferences.add(cb)
                item?.let { menuItemReferences[menuItem.text] = it }
            }

            else -> {
                val cb = createActionCallback(menuItem)
                val item = if (menuItem.isEnabled) {
                    sni.add_menu_action(parentMenu, menuItem.text, cb, null)
                } else {
                    sni.add_disabled_menu_action(parentMenu, menuItem.text, cb, null)
                }
                callbackReferences.add(cb)
                item?.let { menuItemReferences[menuItem.text] = it }
            }
        }
    }

    private fun createActionCallback(menuItem: MenuItem): SNIWrapper.ActionCallback {
        return object : SNIWrapper.ActionCallback {
            override fun invoke(data: Pointer?) {
                if (running.get()) {
                    menuItem.onClick?.invoke()
                }
            }
        }
    }

    private fun cleanupTray() {
        lock.withLock {
            running.set(false)

            // Clear callbacks first
            activateCallback = null
            secondaryActivateCallback = null
            scrollCallback = null
            callbackReferences.clear()
            menuItemReferences.clear()

            // Destroy menu and tray
            menuHandle?.let { sni.destroy_menu(it) }
            trayHandle?.let { sni.destroy_handle(it) }

            menuHandle = null
            trayHandle = null

            // Shutdown tray system
            sni.shutdown_tray_system()

            println("LinuxTrayManager: Cleaning up tray resources")
        }
    }

    fun stopTray() {
        lock.withLock {
            if (!running.get()) return
            running.set(false)
        }

        // Stop the event loop
        sni.sni_stop_exec()

        // Interrupt the thread to break any blocking calls
        trayThread?.interrupt()

        // Wait for thread to finish with better handling
        trayThread?.let { thread ->
            try {
                thread.join(5000)
                if (thread.isAlive) {
                    println("Warning: Tray thread did not terminate in time, forcing interrupt again")
                    thread.interrupt()
                    thread.join(2000) // Second attempt
                }
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            } finally {
                if (shutdownHook != null && Thread.currentThread() != shutdownHook) {
                    try {
                        Runtime.getRuntime().removeShutdownHook(shutdownHook)
                    } catch (e : IllegalStateException) {
                        // La JVM est déjà en cours d'arrêt : rien à faire.
                    }
                }
                shutdownHook = null
            }
        }

        trayThread = null
    }
}