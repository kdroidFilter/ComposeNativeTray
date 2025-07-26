package com.kdroid.composetray.lib.windows

import com.kdroid.composetray.utils.debugln
import com.sun.jna.Native
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal class WindowsTrayManager(
    private var iconPath: String,
    private var tooltip: String = "",
    private var onLeftClick: (() -> Unit)? = null
) {
    private val trayLib: WindowsNativeTrayLibrary = Native.load("tray", WindowsNativeTrayLibrary::class.java)
    private var tray: WindowsNativeTray = WindowsNativeTray()
    private val running = AtomicBoolean(false)
    private val initialized = AtomicBoolean(false)
    private val updateLock = ReentrantLock()

    // Maintain a reference to all callbacks to avoid GC
    private val callbackReferences: MutableList<com.sun.jna.win32.StdCallLibrary.StdCallCallback> = mutableListOf()
    private val nativeMenuItemsReferences: MutableList<WindowsNativeTrayMenuItem> = mutableListOf()

    // Keep a reference to the tray callback
    private var trayCallback: WindowsNativeTray.TrayCallback? = null

    // Coroutine for running the tray loop
    private var trayJob: Job? = null
    private val trayScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        private fun log(message: String) {
            debugln { "[WindowsTrayManager] $message" }
        }
    }

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
        log("initialize() called with ${menuItems.size} menu items")
        updateLock.withLock {
            if (initialized.get()) {
                log("Already initialized, delegating to update()")
                update(iconPath, tooltip, onLeftClick, menuItems)
                return
            }

            // Set up the left click callback
            setupLeftClickCallback()

            // Set up the menu
            setupMenu(menuItems)

            // Initialize the tray
            log("Calling tray_init()")
            val initResult = trayLib.tray_init(tray)
            log("tray_init() returned: $initResult")
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
        log("update() called - icon: $newIconPath, tooltip: $newTooltip, menuItems: ${newMenuItems.size}")
        updateLock.withLock {
            if (!initialized.get()) {
                log("Not initialized, calling initialize()")
                iconPath = newIconPath
                tooltip = newTooltip
                onLeftClick = newOnLeftClick
                initialize(newMenuItems)
                return
            }

            // Stop current message loop temporarily
            val wasRunning = running.get()
            log("Current running state: $wasRunning")
            if (wasRunning) {
                running.set(false)
                log("Stopping message loop...")
                runBlocking {
                    trayJob?.cancelAndJoin()
                }
                log("Message loop stopped")
            }

            // Update properties
            iconPath = newIconPath
            tooltip = newTooltip
            onLeftClick = newOnLeftClick
            val oldCallbackCount = callbackReferences.size

            // Create a new tray structure to ensure clean state
            tray = WindowsNativeTray().apply {
                icon_filepath = newIconPath
                tooltip = newTooltip
            }

            // Update left click callback
            setupLeftClickCallback()

            // Clear old references
            callbackReferences.clear()
            nativeMenuItemsReferences.clear()
            log("Cleared $oldCallbackCount old callbacks")

            // Set up new menu
            setupMenu(newMenuItems)
            log("New callbacks count: ${callbackReferences.size}")

            // Update the native tray
            log("Calling tray_update()")
            trayLib.tray_update(tray)
            log("tray_update() completed")

            // Restart message loop
            if (wasRunning) {
                running.set(true)
                startMessageLoop()
                log("Message loop restarted")
            }
        }
    }

    private fun setupLeftClickCallback() {
        trayCallback = if (onLeftClick != null) {
            log("Setting up left click callback")
            object : WindowsNativeTray.TrayCallback {
                override fun invoke(tray: WindowsNativeTray) {
                    log("Left click callback invoked")
                    try {
                        onLeftClick?.invoke()
                    } catch (e: Exception) {
                        log("Error in left click callback: ${e.message}")
                        e.printStackTrace()
                    }
                }
            }
        } else {
            log("No left click callback set")
            null
        }
        tray.cb = trayCallback
        if (trayCallback != null) {
            callbackReferences.add(trayCallback!!)
        }
    }

    private fun setupMenu(menuItems: List<MenuItem>) {
        if (menuItems.isEmpty()) {
            log("No menu items to set up")
            tray.menu = null
            return
        }

        log("Setting up ${menuItems.size} menu items")
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
            val callback = object : StdCallCallback {
                override fun invoke(item: WindowsNativeTrayMenuItem) {
                    log("Menu item clicked: ${menuItem.text}")
                    try {
                        if (running.get()) {
                            onClick()
                            if (menuItem.isCheckable) {
                                item.checked = if (item.checked == 0) 1 else 0
                                item.write()
                                updateLock.withLock {
                                    trayLib.tray_update(tray)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        log("Error in menu item callback: ${e.message}")
                        e.printStackTrace()
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
        log("Starting message loop")
        trayJob?.cancel() // Cancel any existing job

        trayJob = trayScope.launch {
            // Add a small delay to ensure Windows has processed the update
            delay(50)

            var loopCount = 0
            while (running.get() && isActive) {
                try {
                    val result = trayLib.tray_loop(0) // Non-blocking
                    if (loopCount % 1000 == 0) {
                        log("Message loop running... (iteration $loopCount, result: $result)")
                    }
                    loopCount++

                    when (result) {
                        -1 -> {
                            log("tray_loop returned -1 (error or quit)")
                            if (running.get() && initialized.get()) {
                                // Try to recover by re-adding the tray icon
                                delay(100)
                                try {
                                    updateLock.withLock {
                                        log("Attempting to recover tray...")
                                        trayLib.tray_update(tray)
                                    }
                                } catch (e: Exception) {
                                    log("Failed to recover: ${e.message}")
                                    e.printStackTrace()
                                }
                            } else {
                                break
                            }
                        }
                        0 -> {
                            // Normal operation
                            delay(10)
                        }
                        else -> {
                            log("tray_loop returned unexpected value: $result")
                            delay(10)
                        }
                    }
                } catch (e: Exception) {
                    log("Exception in message loop: ${e.message}")
                    if (running.get()) {
                        e.printStackTrace()
                        delay(100)
                    } else {
                        break
                    }
                }
            }
            log("Message loop ended")
        }
    }

    fun stopTray() {
        log("stopTray() called")
        updateLock.withLock {
            running.set(false)

            // Cancel the coroutine
            runBlocking {
                trayJob?.cancelAndJoin()
            }

            if (initialized.get()) {
                try {
                    log("Calling tray_exit()")
                    trayLib.tray_exit()
                } catch (e: Exception) {
                    log("Error in tray_exit(): ${e.message}")
                    e.printStackTrace()
                }
                initialized.set(false)
            }

            // Clear all references
            callbackReferences.clear()
            nativeMenuItemsReferences.clear()
            trayCallback = null
            log("Tray stopped and cleaned up")
        }
    }
}