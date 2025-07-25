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
    private fun runOnTrayThread(action: () -> Unit) { taskQueue += action }

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

    fun updateMenuItemCheckedState(label: String, isChecked: Boolean) {
        lock.withLock {
            val index = menuItems.indexOfFirst { it.text == label }
            if (index != -1) {
                menuItems[index] = menuItems[index].copy(isChecked = isChecked)
                // Queue menu recreation to be executed in the tray thread
                runOnTrayThread {
                    recreateMenu()
                }
            }
        }
    }

    fun update(
        newIconPath: String,
        newTooltip: String,
        newOnLeftClick: (() -> Unit)?,
        newPrimaryActionLabel: String,
        newMenuItems: List<MenuItem>? = null
    ) {
        lock.withLock {
            if (!running.get() || trayHandle == null) return

            // Update properties
            val iconChanged = this.iconPath != newIconPath
            val tooltipChanged = this.tooltip != newTooltip

            this.iconPath = newIconPath
            this.tooltip = newTooltip
            this.onLeftClick = newOnLeftClick
            this.primaryActionLabel = newPrimaryActionLabel

            taskQueue += {
                // === Ces instructions s'exécutent maintenant dans le même thread GLib ===
                if (iconChanged)    sni.update_icon_by_path(trayHandle, newIconPath)
                if (tooltipChanged) sni.set_tooltip_title(trayHandle, newTooltip)

                if (newMenuItems != null) {
                    menuItems.clear()
                    menuItems.addAll(newMenuItems)
                    recreateMenu()              // recréation sûre du menu
                }
            }
        }
    }

    private fun recreateMenu() {
        if (!running.get() || trayHandle == null) return

        // Destroy old menu
        menuHandle?.let { sni.destroy_menu(it) }
        menuHandle = null

        // Clear old references
        callbackReferences.clear()
        menuItemReferences.clear()

        // Recreate the menu
        if (menuItems.isNotEmpty()) {
            menuHandle = sni.create_menu()
            if (menuHandle != null) {
                menuItems.forEach { item ->
                    addNativeMenuItem(menuHandle!!, item)
                }
                sni.set_context_menu(trayHandle, menuHandle)
            }
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
                    Thread.sleep(16)            // petit yield
                }
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
                if (shutdownHook != null) {
                    Runtime.getRuntime().removeShutdownHook(shutdownHook)
                    shutdownHook = null
                }
            }
        }

        trayThread = null
    }
}