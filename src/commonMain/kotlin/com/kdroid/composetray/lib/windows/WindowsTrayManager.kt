package com.kdroid.composetray.lib.windows

import com.kdroid.composetray.utils.debugln
import com.kdroid.composetray.utils.TrayClickTracker
import com.sun.jna.ptr.IntByReference
import kotlinx.coroutines.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal class WindowsTrayManager(
    private val instanceId: String,
    private var iconPath: String,
    private var tooltip: String = "",
    private var onLeftClick: (() -> Unit)? = null
) {
    private var tray: AtomicReference<WindowsNativeTray?> = AtomicReference(null)
    private val running = AtomicBoolean(false)
    private val initialized = AtomicBoolean(false)
    private val updateLock = ReentrantLock()
    private val initLatch = CountDownLatch(1)

    // Maintain a reference to all callbacks to avoid GC
    private val callbackReferences: MutableList<com.sun.jna.win32.StdCallLibrary.StdCallCallback> = mutableListOf()
    private val nativeMenuItemsReferences: MutableList<WindowsNativeTrayMenuItem> = mutableListOf()

    // Keep a reference to the tray callback
    private var trayCallback: WindowsNativeTray.TrayCallback? = null

    // Thread for running the tray (similar to macOS)
    private var trayThread: Thread? = null

    // Coroutine scopes for callback handling
    private var mainScope: CoroutineScope? = null
    private var ioScope: CoroutineScope? = null

    // Queue for updates to be processed on the tray thread
    private val updateQueue = mutableListOf<UpdateRequest>()
    private val updateQueueLock = Object()

    companion object {
        private fun log(message: String) {
            debugln { "[WindowsTrayManager] $message" }
        }
    }

    // Update request data class
    private data class UpdateRequest(
        val iconPath: String,
        val tooltip: String,
        val onLeftClick: (() -> Unit)?,
        val menuItems: List<MenuItem>
    )

    // Top level MenuItem class
    data class MenuItem(
        val text: String,
        val iconPath: String? = null,  // Added icon path support
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

            running.set(true)

            // Create coroutine scopes
            mainScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
            ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

            // Create and start the tray thread
            trayThread = Thread {
                try {
                    log("Tray thread started")

                    // Create tray structure on this thread
                    val newTray = WindowsNativeTray().apply {
                        icon_filepath = iconPath
                        tooltip = this@WindowsTrayManager.tooltip
                    }

                    // Set up callbacks and menu on this thread
                    setupLeftClickCallback(newTray)
                    setupMenu(newTray, menuItems)

                    // Initialize the tray on this thread
                    log("Calling tray_init() on tray thread")
                    val initResult = WindowsNativeTrayLibrary.tray_init(newTray)
                    log("tray_init() returned: $initResult")

                    if (initResult != 0) {
                        throw RuntimeException("Failed to initialize tray: $initResult")
                    }

                    tray.set(newTray)
                    initialized.set(true)

                    // Signal that initialization is complete
                    initLatch.countDown()

                    // Run the blocking message loop on this thread
                    runMessageLoop()

                } catch (e: Exception) {
                    log("Error in tray thread: ${e.message}")
                    e.printStackTrace()
                    initLatch.countDown() // Ensure latch is released even on error
                } finally {
                    cleanupTray()
                }
            }.apply {
                name = "WindowsTray-Thread"
                isDaemon = false // Don't make it daemon so it can clean up properly
                start()
            }

            // Wait for initialization to complete
            try {
                initLatch.await()
            } catch (e: InterruptedException) {
                log("Interrupted while waiting for initialization")
                e.printStackTrace()
            }
        }
    }

    fun update(newIconPath: String, newTooltip: String, newOnLeftClick: (() -> Unit)?, newMenuItems: List<MenuItem>) {
        log("update() called - icon: $newIconPath, tooltip: $newTooltip, menuItems: ${newMenuItems.size}")

        if (!initialized.get()) {
            log("Not initialized, calling initialize()")
            iconPath = newIconPath
            tooltip = newTooltip
            onLeftClick = newOnLeftClick
            initialize(newMenuItems)
            return
        }

        // Queue the update to be processed on the tray thread
        synchronized(updateQueueLock) {
            updateQueue.add(UpdateRequest(newIconPath, newTooltip, newOnLeftClick, newMenuItems))
            updateQueueLock.notify()
        }
    }

    private fun runMessageLoop() {
        log("Entering message loop on tray thread")
        var consecutiveErrors = 0
        var initialPosCaptured = false
        var initialPosAttempts = 0

        while (running.get()) {
            try {
                // Try to capture initial precise tray icon position (per instance)
                if (!initialPosCaptured && initialPosAttempts < 60) {
                    initialPosAttempts++
                    try {
                        val xRef = IntByReference()
                        val yRef = IntByReference()
                        val precise = WindowsNativeTrayLibrary.tray_get_notification_icons_position(xRef, yRef) != 0
                        if (precise) {
                            val screen = java.awt.Toolkit.getDefaultToolkit().screenSize
                            val corner = com.kdroid.composetray.utils.convertPositionToCorner(xRef.value, yRef.value, screen.width, screen.height)
                            TrayClickTracker.setClickPosition(instanceId, xRef.value, yRef.value, corner)
                            initialPosCaptured = true
                            log("Captured initial tray icon position: ${xRef.value}, ${yRef.value}")
                        }
                    } catch (e: Exception) {
                        // ignore and retry later
                    }
                }
                // Check for pending updates
                processUpdateQueue()

                // Process Windows messages with blocking call for responsiveness
                val result = WindowsNativeTrayLibrary.tray_loop(0)

                when (result) {
                    -1 -> {
                        log("tray_loop returned -1 (error or quit)")
                        if (running.get() && initialized.get()) {
                            consecutiveErrors++
                            if (consecutiveErrors > 5) {
                                log("Too many consecutive errors, exiting loop")
                                break
                            }
                            Thread.sleep(100)

                            // Try to recover
                            val currentTray = tray.get()
                            if (currentTray != null) {
                                try {
                                    log("Attempting to recover tray...")
                                    WindowsNativeTrayLibrary.tray_update(currentTray)
                                    consecutiveErrors = 0
                                } catch (e: Exception) {
                                    log("Failed to recover: ${e.message}")
                                    e.printStackTrace()
                                }
                            }
                        } else {
                            break
                        }
                    }
                    0 -> {
                        // Normal operation
                        consecutiveErrors = 0
                    }
                    else -> {
                        log("tray_loop returned unexpected value: $result")
                        consecutiveErrors = 0
                    }
                }
                Thread.sleep(50)
            } catch (e: Exception) {
                log("Exception in message loop: ${e.message}")
                if (running.get()) {
                    e.printStackTrace()
                    Thread.sleep(100)
                } else {
                    break
                }
            }
        }
        log("Message loop ended")
    }

    private fun processUpdateQueue() {
        val update = synchronized(updateQueueLock) {
            if (updateQueue.isNotEmpty()) {
                updateQueue.removeAt(0)
            } else {
                null
            }
        }

        if (update != null) {
            log("Processing update from queue")
            performUpdate(update)
        }
    }

    private fun performUpdate(update: UpdateRequest) {
        // Update properties
        iconPath = update.iconPath
        tooltip = update.tooltip
        onLeftClick = update.onLeftClick

        // Clear old references
        val oldCallbackCount = callbackReferences.size
        callbackReferences.clear()
        nativeMenuItemsReferences.clear()
        log("Cleared $oldCallbackCount old callbacks")

        // Create a new tray structure
        val newTray = WindowsNativeTray().apply {
            icon_filepath = update.iconPath
            tooltip = update.tooltip
        }

        // Set up new callbacks and menu
        setupLeftClickCallback(newTray)
        setupMenu(newTray, update.menuItems)

        // Update the native tray
        log("Calling tray_update()")
        WindowsNativeTrayLibrary.tray_update(newTray)
        log("tray_update() completed")

        // Update the reference
        tray.set(newTray)
    }

    private fun setupLeftClickCallback(trayObj: WindowsNativeTray) {
        trayCallback = if (onLeftClick != null) {
            log("Setting up left click callback")
            object : WindowsNativeTray.TrayCallback {
                override fun invoke(tray: WindowsNativeTray) {
                    log("Left click callback invoked")
                    try {
                        // Capture precise tray position on the tray thread (per-instance)
                        val xRef = IntByReference()
                        val yRef = IntByReference()
                        val precise = WindowsNativeTrayLibrary.tray_get_notification_icons_position(xRef, yRef) != 0
                        if (precise) {
                            val screen = java.awt.Toolkit.getDefaultToolkit().screenSize
                            val corner = com.kdroid.composetray.utils.convertPositionToCorner(xRef.value, yRef.value, screen.width, screen.height)
                            TrayClickTracker.setClickPosition(instanceId, xRef.value, yRef.value, corner)
                        }

                        // Execute callback in IO scope (like macOS)
                        mainScope?.launch {
                            ioScope?.launch {
                                onLeftClick?.invoke()
                            }
                        }
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
        trayObj.cb = trayCallback
        if (trayCallback != null) {
            callbackReferences.add(trayCallback!!)
        }
    }

    private fun setupMenu(trayObj: WindowsNativeTray, menuItems: List<MenuItem>) {
        if (menuItems.isEmpty()) {
            log("No menu items to set up")
            trayObj.menu = null
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

        trayObj.menu = nativeMenuItems[0].pointer
    }

    private fun initializeNativeMenuItem(nativeItem: WindowsNativeTrayMenuItem, menuItem: MenuItem) {
        nativeItem.text = menuItem.text
        nativeItem.icon_path = menuItem.iconPath
        nativeItem.disabled = if (menuItem.isEnabled) 0 else 1
        nativeItem.checked = if (menuItem.isChecked) 1 else 0

        // Create the menu item callback
        menuItem.onClick?.let { onClick ->
            val callback = object : StdCallCallback {
                override fun invoke(item: WindowsNativeTrayMenuItem) {
                    log("Menu item clicked: ${menuItem.text}")
                    try {
                        // Capture precise tray position on the tray thread (per-instance)
                        val xRef = IntByReference()
                        val yRef = IntByReference()
                        val precise = WindowsNativeTrayLibrary.tray_get_notification_icons_position(xRef, yRef) != 0
                        if (precise) {
                            val screen = java.awt.Toolkit.getDefaultToolkit().screenSize
                            val corner = com.kdroid.composetray.utils.convertPositionToCorner(xRef.value, yRef.value, screen.width, screen.height)
                            TrayClickTracker.setClickPosition(instanceId, xRef.value, yRef.value, corner)
                        }

                        if (running.get()) {
                            // Execute callback in IO scope (like macOS)
                            mainScope?.launch {
                                ioScope?.launch {
                                    onClick()
                                    if (menuItem.isCheckable) {
                                        // Queue an update to refresh the checked state
                                        synchronized(updateQueueLock) {
                                            // We need to update the menu with the new checked state
                                            // This would require keeping track of menu state
                                            // For now, the update will come from the application
                                        }
                                    }
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

    private fun cleanupTray() {
        if (initialized.get()) {
            try {
                log("Calling tray_exit()")
                WindowsNativeTrayLibrary.tray_exit()
            } catch (e: Exception) {
                log("Error in tray_exit(): ${e.message}")
                e.printStackTrace()
            }
        }

        // Clear all references
        callbackReferences.clear()
        nativeMenuItemsReferences.clear()
        trayCallback = null
        tray.set(null)

        initialized.set(false)
    }

    fun stopTray() {
        log("stopTray() called")
        updateLock.withLock {
            running.set(false)

            // Wake up the thread if it's waiting
            synchronized(updateQueueLock) {
                updateQueueLock.notify()
            }

            // Wait for the tray thread to finish
            trayThread?.let { thread ->
                try {
                    thread.join(5000) // Wait up to 5 seconds
                    if (thread.isAlive) {
                        log("Tray thread still alive after 5 seconds, interrupting")
                        thread.interrupt()
                    }
                } catch (e: InterruptedException) {
                    log("Interrupted while waiting for tray thread")
                    e.printStackTrace()
                }
            }

            // Cancel coroutines
            mainScope?.cancel()
            ioScope?.cancel()
            mainScope = null
            ioScope = null
            trayThread = null

            log("Tray stopped and cleaned up")
        }
    }
}