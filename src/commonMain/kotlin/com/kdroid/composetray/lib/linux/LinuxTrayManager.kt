package com.kdroid.composetray.lib.linux

import com.kdroid.composetray.utils.*
import com.sun.jna.Pointer
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Manages an SNI‑based tray icon on Linux.
 *
 * <h3>Changelog 2025‑07‑26</h3>
 * • Deduplication of menu refresh via `refreshPending`.<br/>
 * • <em>New:</em> Incremental update of item checkable state without
 *   rebuilding the entire menu: `updateMenuItemCheckedState()` uses the fast
 *   native path when the GTK/Qt pointer of the item is known, and only falls back
 *   to a complete `recreateMenu()` when necessary.<br/>
 * • <em>New:</em> Incremental icon update without rebuilding
 *   the entire menu: `updateIconPath()` uses the fast native path and only falls back
 *   in case of error.<br/>
 * • <em>New:</em> Capture of click position for precise window placement.
 */
internal class LinuxTrayManager(
    private var iconPath: String,
    private var tooltip: String = "",
    private var onLeftClick: (() -> Unit)? = null,
    private var primaryActionLabel: String
) {
    private val sni = SNIWrapper.INSTANCE

    // Native handles ---------------------------------------------------------------------------
    private var trayHandle: Pointer? = null
    private var menuHandle: Pointer? = null

    // State ------------------------------------------------------------------------------------
    private val menuItems: MutableList<MenuItem> = mutableListOf()
    private val running = AtomicBoolean(false)
    private val lock = ReentrantLock()

    // Threading -------------------------------------------------------------------------------
    private var trayThread: Thread? = null
    private val initLatch = CountDownLatch(1)
    private var shutdownHook: Thread? = null
    private val taskQueue: ConcurrentLinkedQueue<() -> Unit> = ConcurrentLinkedQueue()

    // GC safety -------------------------------------------------------------------------------
    private val callbackReferences: MutableList<Any> = mutableListOf()
    private val menuItemReferences: MutableMap<String, Pointer> = mutableMapOf()

    private var activateCallback: SNIWrapper.ActivateCallback? = null
    private var secondaryActivateCallback: SNIWrapper.SecondaryActivateCallback? = null
    private var scrollCallback: SNIWrapper.ScrollCallback? = null

    // ----------------------------------------------------------------------- menu refresh gate
    private val refreshPending = AtomicBoolean(false)
    private val lastRefreshRequest = AtomicLong(0)
    private val REFRESH_WINDOW_MS = 120L // fusionne les refresh rapprochés
    // Prevent overlapping tray restarts (avoid: destroy -> recreate -> destroyed again)
    private val restartInProgress = AtomicBoolean(false)

    private fun requestMenuRefresh() {
        lastRefreshRequest.set(System.currentTimeMillis())
        if (refreshPending.compareAndSet(false, true)) {
            val t = Thread {
                // petite fenêtre de debounce sans bloquer le thread du tray
                while (System.currentTimeMillis() - lastRefreshRequest.get() < REFRESH_WINDOW_MS) {
                    try {
                        Thread.sleep(REFRESH_WINDOW_MS)
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                        break
                    }
                }
                runOnTrayThread {
                    try {
                        recreateMenu()
                    } finally {
                        refreshPending.set(false)
                    }
                }
            }
            t.name = "LinuxTray-MenuDebounce"
            t.isDaemon = true
            t.start()
        }
    }

    // --------------------------------------------------------------------------- util helpers
    private fun runOnTrayThread(action: () -> Unit) {
        taskQueue += action
    }

    private fun schedule(action: () -> Unit) { // retained for future use by callers
        taskQueue += action
    }

    // ----------------------------------------------------------------------------- menu model
    data class MenuItem(
        val text: String,
        val isEnabled: Boolean = true,
        val isCheckable: Boolean = false,
        val isChecked: Boolean = false,
        val onClick: (() -> Unit)? = null,
        val subMenuItems: List<MenuItem> = emptyList()
    )

    fun addMenuItem(menuItem: MenuItem) {
        lock.withLock { menuItems.add(menuItem) }
    }

    /**
     * Updates the <i>checked / unchecked</i> state of an item in real-time without
     * triggering a complete menu rebuild, whenever possible.
     */
    fun updateMenuItemCheckedState(label: String, isChecked: Boolean) {
        var fallbackRefreshNeeded = false
        lock.withLock {
            // 1. Mettre à jour le modèle mémoire ---------------------------------------------
            val idx = menuItems.indexOfFirst { it.text == label }
            if (idx != -1) {
                menuItems[idx] = menuItems[idx].copy(isChecked = isChecked)
            }

            // 2. Chemin rapide : on possède déjà le pointeur natif de l'item ⇒ mise à jour GTK/Qt
            val ptr = menuItemReferences[label]
            if (ptr != null && trayHandle != null) {
                try {
                    // Méthode à exposer dans SNIWrapper :
                    //    int set_menu_item_checked(Pointer menuItem, int checked)
                    val ok = sni.set_menu_item_checked(ptr, if (isChecked) 1 else 0)
                    if (ok == 0) {
                        // SNI l'a fait, on force le repaint global (léger)
                        sni.tray_update(trayHandle)
                    } else {
                        // Implé pas dispo ou erreur : on repassera par recreateMenu
                        fallbackRefreshNeeded = true
                    }
                } catch (_: UnsatisfiedLinkError) {
                    // Version de lib sans cette fonction : on repassera par recreateMenu
                    fallbackRefreshNeeded = true
                }
            } else {
                // Pointeur pas encore connu (menu pas construit) : on refera un refresh complet
                fallbackRefreshNeeded = true
            }
        }
        if (fallbackRefreshNeeded) requestMenuRefresh()
    }

    // ---------------------------------------------------------------------------------- update
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

        if (needsMenuRefresh) requestMenuRefresh()
    }

    // --------------------------------------------------------------------------- menu creation
    private fun recreateMenu() {
        infoln { "LinuxTrayManager: Recreating menu" }
        if (restartInProgress.get()) {
            infoln { "LinuxTrayManager: Skip recreateMenu — workaround restart in progress" }
            // Reschedule a refresh shortly after the workaround completes so the menu isn't lost
            val retry = Thread {
                try { Thread.sleep(150) } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
                requestMenuRefresh()
            }
            retry.name = "LinuxTray-MenuRetry"
            retry.isDaemon = true
            retry.start()
            return
        }
        if (!running.get() || trayHandle == null) {
            warnln { "LinuxTrayManager: Cannot recreate menu, tray is not running or trayHandle is null" }
            return
        }

        try {
            callbackReferences.clear()
            menuItemReferences.clear()

            if (menuItems.isNotEmpty()) {
                // Create or clear existing menu ------------------------------------------------
                if (menuHandle == null) {
                    menuHandle = sni.create_menu()
                    if (menuHandle == null) {
                        errorln { "LinuxTrayManager: Failed to create menu" }
                        return
                    }
                    infoln { "LinuxTrayManager: Menu created" }
                } else {
                    try {
                        sni.clear_menu(menuHandle)
                        infoln { "LinuxTrayManager: Menu cleared" }
                    } catch (_: Exception) {
                        // Fallback: recreate menu completely when clear_menu is unavailable
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

                // Add menu items --------------------------------------------------------------
                menuItems.forEach { addNativeMenuItem(menuHandle!!, it) }
                sni.set_context_menu(trayHandle, menuHandle)
                infoln { "LinuxTrayManager: Context menu set" }
            } else {
                // No items: workaround for SNI bug when transitioning from non-empty to empty ---
                if (menuHandle != null) {
                    infoln { "LinuxTrayManager: Workaround — recreating tray without context menu (non-empty -> empty transition)" }
                    // Fully recreate the tray without any context menu to force the SNI to refresh
                    restartTrayPreservingStateWithoutMenu()
                    return
                }
            }

            // Force visual update if the API provides it ---------------------------------------
            try {
                sni.tray_update(trayHandle)
                infoln { "LinuxTrayManager: Tray update forced" }
            } catch (e: Exception) {
                warnln { "LinuxTrayManager: tray_update not available: ${e.message}" }
            }
        } catch (e: Exception) {
            errorln { "LinuxTrayManager: Error recreating menu: ${e.message}" }
            e.printStackTrace()
        }
    }

    // ------------------------------------------------------------------------------- tray life
    fun startTray() {
        lock.withLock {
            if (running.get()) return
            running.set(true)
        }

        // Register shutdown hook --------------------------------------------------------------
        shutdownHook = Thread { stopTray() }.also { Runtime.getRuntime().addShutdownHook(it) }

        // Main event loop ---------------------------------------------------------------------
        trayThread = Thread {
            try {
                val initResult = sni.init_tray_system()
                if (initResult != 0) throw IllegalStateException("Failed to initialize tray system: $initResult")

                trayHandle = sni.create_tray("composetray-${System.currentTimeMillis()}")
                if (trayHandle == null) {
                    sni.shutdown_tray_system()
                    throw IllegalStateException("Failed to create tray")
                }

                sni.set_title(trayHandle, "Compose Tray")
                sni.set_status(trayHandle, "Active")
                sni.set_icon_by_path(trayHandle, iconPath)
                sni.set_tooltip_title(trayHandle, tooltip)
                sni.set_tooltip_subtitle(trayHandle, "")

                initializeCallbacks()
                initializeTrayMenu()
                initLatch.countDown()

                // Event processing ----------------------------------------------------------
                while (running.get()) {
                    sni.sni_process_events()
                    while (true) taskQueue.poll()?.invoke() ?: break
                    Thread.sleep(50)
                }
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
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

        try { initLatch.await() } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
    }

    private fun initializeCallbacks() {
        trayHandle?.let { handle ->
            activateCallback = object : SNIWrapper.ActivateCallback {
                override fun invoke(x: Int, y: Int, data: Pointer?) {
                    // Capture click position
                    TrayClickTracker.updateClickPosition(x, y)
                    debugln { "LinuxTrayManager: Tray clicked at position ($x, $y)" }
                    onLeftClick?.invoke()
                }
            }
            sni.set_activate_callback(handle, activateCallback, null)

            secondaryActivateCallback = object : SNIWrapper.SecondaryActivateCallback {
                override fun invoke(x: Int, y: Int, data: Pointer?) {
                    // Also capture right-click position
                    TrayClickTracker.updateClickPosition(x, y)
                    debugln { "LinuxTrayManager: Tray right-clicked at position ($x, $y)" }
                    /* secondary click */
                }
            }
            sni.set_secondary_activate_callback(handle, secondaryActivateCallback, null)

            scrollCallback = object : SNIWrapper.ScrollCallback {
                override fun invoke(delta: Int, orientation: Int, data: Pointer?) { /* scroll */ }
            }
            sni.set_scroll_callback(handle, scrollCallback, null)
        }
    }

    private fun initializeTrayMenu() {
        if (menuItems.isEmpty()) return
        menuHandle = sni.create_menu() ?: run {
            errorln { "Failed to create menu" }
            return
        }
        menuItems.forEach { addNativeMenuItem(menuHandle!!, it) }
        trayHandle?.let { sni.set_context_menu(it, menuHandle) }
    }

    private fun addNativeMenuItem(parentMenu: Pointer, menuItem: MenuItem) {
        when {
            menuItem.text == "-" -> sni.add_menu_separator(parentMenu)
            menuItem.subMenuItems.isNotEmpty() -> {
                val submenu = sni.create_submenu(parentMenu, menuItem.text)
                if (submenu != null) {
                    menuItemReferences[menuItem.text] = submenu
                    menuItem.subMenuItems.forEach { addNativeMenuItem(submenu, it) }
                }
            }
            menuItem.isCheckable -> {
                val cb = createActionCallback(menuItem)
                val item = sni.add_checkable_menu_action(parentMenu, menuItem.text, if (menuItem.isChecked) 1 else 0, cb, null)
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

    private fun createActionCallback(menuItem: MenuItem): SNIWrapper.ActionCallback =
        object : SNIWrapper.ActionCallback {
            override fun invoke(data: Pointer?) {
                if (running.get()) menuItem.onClick?.invoke()
            }
        }

    private fun cleanupTray() {
        lock.withLock {
            running.set(false)
            activateCallback = null
            secondaryActivateCallback = null
            scrollCallback = null
            callbackReferences.clear()
            menuItemReferences.clear()
            menuHandle?.let { sni.destroy_menu(it) }
            trayHandle?.let { sni.destroy_handle(it) }
            menuHandle = null
            trayHandle = null
            sni.shutdown_tray_system()
            infoln { "LinuxTrayManager: Cleaning up tray resources" }
        }
    }

    fun stopTray() {
        lock.withLock { if (!running.get()) return; running.set(false) }
        sni.sni_stop_exec()
        trayThread?.interrupt()
        trayThread?.let { t ->
            try {
                t.join(5_000)
                if (t.isAlive) {
                    warnln { "Warning: Tray thread did not terminate in time, forcing interrupt again" }
                    t.interrupt()
                    t.join(2_000)
                }
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            } finally {
                if (shutdownHook != null && Thread.currentThread() != shutdownHook) {
                    try { Runtime.getRuntime().removeShutdownHook(shutdownHook) } catch (_: IllegalStateException) {}
                }
                shutdownHook = null
            }
        }
        trayThread = null
    }

    private fun restartTrayPreservingStateWithoutMenu() {
        // Called on tray thread. Recreate the tray handle without attaching any context menu.
        if (!running.get()) return
        // Prevent overlapping restarts that could destroy the freshly recreated tray
        if (!restartInProgress.compareAndSet(false, true)) {
            infoln { "LinuxTrayManager: Workaround restart skipped (already in progress)" }
            return
        }
        try {
            // Prevent native auto-shutdown while we fully restart the tray
            try { sni.sni_begin_restart_guard() } catch (_: Throwable) {}

            val currentIcon = iconPath
            val currentTooltip = tooltip

            // Detach and destroy any existing menu first (from the old handle)
            try {
                if (menuHandle != null) {
                    try { sni.set_context_menu(trayHandle, null) } catch (_: Exception) {}
                    try { Thread.sleep(50) } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
                    try { sni.destroy_menu(menuHandle!!) } catch (_: Exception) {}
                    menuHandle = null
                }
            } catch (_: Exception) { }

            // Destroy the old tray handle FIRST to ensure there is never a moment with two icons
            val oldHandle = trayHandle
            try {
                if (oldHandle != null) {
                    sni.destroy_handle(oldHandle)
                }
            } catch (_: Exception) {
                warnln { "LinuxTrayManager: Failed to destroy old tray handle during workaround" }
            }
            trayHandle = null

            // Give the host time to remove the old icon (DBus async). We can wait longer now thanks to native restart guard
            infoln { "LinuxTrayManager: Waiting for host to remove old icon (up to 600ms)" }
            val waitStart = System.currentTimeMillis()
            while (System.currentTimeMillis() - waitStart < 600) {
                try { sni.sni_process_events() } catch (_: Exception) {}
                try { Thread.sleep(10) } catch (_: InterruptedException) { Thread.currentThread().interrupt(); break }
            }

            // Recreate new tray handle (native layer will skip shutdown if a new tray appears)
            val newHandle = sni.create_tray("composetray-${System.currentTimeMillis()}")
            if (newHandle == null) {
                errorln { "LinuxTrayManager: Workaround failed — could not recreate tray handle" }
                return
            }

            // Restore properties on the new handle
            sni.set_title(newHandle, "Compose Tray")
            sni.set_status(newHandle, "Active")
            sni.set_icon_by_path(newHandle, currentIcon)
            sni.set_tooltip_title(newHandle, currentTooltip)
            sni.set_tooltip_subtitle(newHandle, "")

            // Switch to the new handle for subsequent operations
            trayHandle = newHandle

            // Re-register callbacks on the new handle
            initializeCallbacks()

            // Ensure no context menu is attached in this workaround
            try { sni.set_context_menu(newHandle, null) } catch (_: Exception) {}

            // Force a visual update
            try {
                sni.tray_update(newHandle)
            } catch (e: Exception) {
                warnln { "LinuxTrayManager: tray_update not available after workaround: ${e.message}" }
            }

            infoln { "LinuxTrayManager: Tray recreated without context menu" }
        } finally {
            try { sni.sni_end_restart_guard() } catch (_: Throwable) {}
            restartInProgress.set(false)
        }
    }
}