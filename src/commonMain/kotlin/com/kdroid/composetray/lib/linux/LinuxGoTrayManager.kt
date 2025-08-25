package com.kdroid.composetray.lib.linux

import com.kdroid.composetray.utils.debugln
import com.kdroid.composetray.utils.errorln
import com.kdroid.composetray.utils.infoln
import com.kdroid.composetray.utils.warnln
import com.kdroid.composetray.utils.TrayClickTracker
import com.kdroid.composetray.utils.getTrayPosition
import io.github.kdroidfilter.platformtools.LinuxDesktopEnvironment
import io.github.kdroidfilter.platformtools.detectLinuxDesktopEnvironment
import java.awt.Toolkit
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Go-based Linux tray manager using the linuxlibnew JNA bridge.
 *
 * Intentionally mirrors the public surface used by LinuxSNITrayInitializer and LinuxTrayMenuBuilderImpl,
 * while delegating to GoSystray under the hood.
 */
internal class LinuxGoTrayManager(
    private val instanceId: String,
    private var iconPath: String,
    private var tooltip: String = "",
    private var onLeftClick: (() -> Unit)? = null
) : LinuxTrayController {
    companion object {
        // Ensures only one systray runtime is active at a time; allows release from any thread
        private val lifecyclePermit = java.util.concurrent.Semaphore(1, true)
    }
    // We reuse the existing MenuItem model from LinuxTrayManager to avoid touching menu builder code
    data class MenuItem(
        val text: String,
        val isEnabled: Boolean = true,
        val isCheckable: Boolean = false,
        val isChecked: Boolean = false,
        val iconPath: String? = null,
        val onClick: (() -> Unit)? = null,
        val subMenuItems: List<LinuxTrayManager.MenuItem> = emptyList() // accept original type for smoother interop
    )

    private val go = GoSystray.INSTANCE

    private val lock = ReentrantLock()
    private val running = AtomicBoolean(false)
    private val permitHeld = AtomicBoolean(false)

    // Menu state built by builder
    private val menuItems: MutableList<LinuxTrayManager.MenuItem> = mutableListOf()

    // Mapping from menu item title to Go-assigned IDs (best-effort; titles should be unique)
    private val idByTitle: MutableMap<String, Int> = mutableMapOf()
    private val actionById: MutableMap<Int, () -> Unit> = mutableMapOf()

    // KDE environment detection to mirror C++ backend behavior
    private fun isKDEDesktop(): Boolean = detectLinuxDesktopEnvironment() == LinuxDesktopEnvironment.KDE

    // Thread / lifecycle
    private var shutdownHook: Thread? = null
    private var loopThread: Thread? = null
    private var exitLatch: CountDownLatch? = null

    override fun addMenuItem(menuItem: LinuxTrayManager.MenuItem) {
        lock.withLock { menuItems.add(menuItem) }
    }

    override fun updateMenuItemCheckedState(label: String, isChecked: Boolean) {
        var fallback = false
        lock.withLock {
            val idx = menuItems.indexOfFirst { it.text == label }
            if (idx != -1) {
                val current = menuItems[idx]
                menuItems[idx] = current.copy(isChecked = isChecked)
            }
            val id = idByTitle[label]
            if (id != null) {
                try {
                    if (isChecked) go.Systray_MenuItem_Check(id) else go.Systray_MenuItem_Uncheck(id)
                } catch (_: Throwable) { fallback = true }
            } else {
                fallback = true
            }
        }
        if (fallback) rebuildMenu()
    }

    override fun update(
        newIconPath: String,
        newTooltip: String,
        newOnLeftClick: (() -> Unit)?,
        newMenuItems: List<LinuxTrayManager.MenuItem>?
    ) {
        val iconChanged: Boolean
        val tooltipChanged: Boolean
        lock.withLock {
            if (!running.get()) return
            iconChanged = (iconPath != newIconPath)
            tooltipChanged = (tooltip != newTooltip)
            iconPath = newIconPath
            tooltip = newTooltip
            onLeftClick = newOnLeftClick
            if (newMenuItems != null) {
                menuItems.clear()
                menuItems.addAll(newMenuItems)
            }
        }

        if (iconChanged) setIconFromFileSafe(iconPath)
        if (tooltipChanged) runCatching { go.Systray_SetTooltip(tooltip) }
            .onFailure { e -> warnln { "LinuxGoTrayManager: Failed to set tooltip: ${e.message}" } }

        if (newMenuItems != null) rebuildMenu()
    }

    override fun startTray() {
        // Acquire global lifecycle permit to prevent overlap with a previous instance teardown
        try {
            lifecyclePermit.acquire()
            permitHeld.set(true)
        } catch (t: Throwable) {
            warnln { "LinuxGoTrayManager: Failed to acquire lifecycle permit: ${t.message}" }
            return
        }

        var started = false
        try {
            if (!running.compareAndSet(false, true)) {
                // Already running for this instance; release permit we just acquired
                lifecyclePermit.release()
                permitHeld.set(false)
                return
            }

            // Shutdown hook
            shutdownHook = Thread { stopTray() }.also { Runtime.getRuntime().addShutdownHook(it) }

            val readyLatch = CountDownLatch(1)
            exitLatch = CountDownLatch(1)

            // Register callbacks
            go.Systray_InitCallbacks(
                object : GoSystray.VoidCallback { override fun invoke() {
                    infoln { "LinuxGoTrayManager: systray ready" }
                    readyLatch.countDown()
                } },
                object : GoSystray.VoidCallback { override fun invoke() {
                    infoln { "LinuxGoTrayManager: systray exit" }
                    try { exitLatch?.countDown() } catch (_: Throwable) {}
                } },
                object : GoSystray.VoidCallback { override fun invoke() {
                    // Try to fetch the last click xy from native Go layer and store it for positioning
                    try {
                        val xRef = com.sun.jna.ptr.IntByReference()
                        val yRef = com.sun.jna.ptr.IntByReference()
                        go.Systray_GetLastClickXY(xRef, yRef)
                        val x = xRef.value
                        val y = yRef.value
                        // Infer corner and persist for Linux positioning
                        val screen = try { Toolkit.getDefaultToolkit().screenSize } catch (_: Throwable) { java.awt.Dimension(0,0) }
                        val pos = com.kdroid.composetray.utils.convertPositionToCorner(x, y, screen.width, screen.height)
                        TrayClickTracker.setClickPosition(x, y, pos)
                    } catch (_: Throwable) {
                        // ignore, still invoke user callback
                    }
                    onLeftClick?.invoke()
                } },
                object : GoSystray.VoidCallback { override fun invoke() { /* right click unhandled for now */ } },
                object : GoSystray.MenuItemCallback { override fun invoke(menuId: Int) { actionById[menuId]?.invoke() } }
            )

            // Prepare external loop and start it in a daemon thread
            go.Systray_PrepareExternalLoop()
            loopThread = Thread({
                try { go.Systray_NativeStart() } catch (t: Throwable) { errorln { "LinuxGoTrayManager: loop error: $t" } }
            }, "LinuxGoTray-Loop").apply {
                isDaemon = true
                start()
            }

            // Wait until ready then set properties and build menu
            try { readyLatch.await() } catch (_: InterruptedException) { Thread.currentThread().interrupt() }

            runCatching { go.Systray_SetTitle("Compose Tray") }
            setIconFromFileSafe(iconPath)
            runCatching { go.Systray_SetTooltip(tooltip) }
            rebuildMenu()
            started = true
        } catch (t: Throwable) {
            errorln { "LinuxGoTrayManager: startTray failed: $t" }
        } finally {
            if (!started) {
                // cleanup partial start and release permit
                running.set(false)
                try { go.Systray_NativeEnd() } catch (_: Throwable) {}
                try { loopThread?.join(500) } catch (_: Throwable) {}
                loopThread = null
                exitLatch = null
                try { shutdownHook?.let { Runtime.getRuntime().removeShutdownHook(it) } } catch (_: Throwable) {}
                shutdownHook = null
                if (permitHeld.compareAndSet(true, false)) {
                    try { lifecyclePermit.release() } catch (_: Throwable) {}
                }
            }
        }
    }

    override fun stopTray() {
        if (!running.compareAndSet(true, false)) return
        val latch = exitLatch
        try {
            // 1) Ask Go to quit its loop
            go.Systray_Quit()
        } catch (_: Throwable) {}

        try {
            // 2) End native side immediately so Go can invoke systrayExit (exit callback)
            go.Systray_NativeEnd()
        } catch (_: Throwable) {}

        try {
            // 3) Wait a short time for the exit callback to arrive (it will countDown the latch)
            //    No need to wait seconds — 150–300ms is enough on healthy paths.
            latch?.await(150, MILLISECONDS)
        } catch (_: Throwable) {}

        try {
            // 4) Join the loop thread briefly; it's a daemon anyway
            loopThread?.join(500)
            if (loopThread?.isAlive == true) {
                // We can't interrupt native waits; just log and move on
                warnln { "LinuxGoTrayManager: loop thread still alive after join timeout" }
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }

        loopThread = null
        exitLatch = null
        idByTitle.clear()
        actionById.clear()
        try { shutdownHook?.let { Runtime.getRuntime().removeShutdownHook(it) } } catch (_: Throwable) {}
        shutdownHook = null
        if (permitHeld.compareAndSet(true, false)) {
            try { lifecyclePermit.release() } catch (_: Throwable) {}
        }
    }


    // ----------------------------------------------------------------------------------------
    private fun setIconFromFileSafe(path: String) {
        runCatching {
            val file = File(path)
            if (file.isFile) {
                val bytes = file.readBytes()
                go.Systray_SetIcon(bytes, bytes.size)
            } else {
                warnln { "LinuxGoTrayManager: Icon file not found: $path" }
            }
        }.onFailure { e -> warnln { "LinuxGoTrayManager: Failed to set icon from $path: ${e.message}" } }
    }

    private fun rebuildMenu() {
        infoln { "LinuxGoTrayManager: Rebuilding menu" }
        idByTitle.clear()
        actionById.clear()
        runCatching { go.Systray_ResetMenu() }
        val items = lock.withLock { menuItems.toList() }
        val effectiveItems = if (items.isEmpty() && isKDEDesktop()) listOf(LinuxTrayManager.MenuItem("-")) else items
        effectiveItems.forEach { addMenuItemRecursive(null, it) }
    }

    private fun addMenuItemRecursive(parentId: Int?, item: LinuxTrayManager.MenuItem) {
        try {
            if (item.text == "-") {
                // Only supported at root by our bridge; if in submenu, emulate with disabled dash item
                if (parentId == null) {
                    go.Systray_AddSeparator()
                } else {
                    val id = if (item.isCheckable) go.Systray_AddSubMenuItemCheckbox(parentId, "-", null, if (item.isChecked) 1 else 0)
                    else go.Systray_AddSubMenuItem(parentId, "-", null)
                    idByTitle[item.text] = id
                    go.Systray_MenuItem_Disable(id)
                }
                return
            }

            val id = if (parentId == null) {
                if (item.isCheckable) go.Systray_AddMenuItemCheckbox(item.text, null, if (item.isChecked) 1 else 0)
                else go.Systray_AddMenuItem(item.text, null)
            } else {
                if (item.isCheckable) go.Systray_AddSubMenuItemCheckbox(parentId, item.text, null, if (item.isChecked) 1 else 0)
                else go.Systray_AddSubMenuItem(parentId, item.text, null)
            }
            idByTitle[item.text] = id
            item.onClick?.let { actionById[id] = it }

            // Enable/Disable
            if (item.isEnabled) go.Systray_MenuItem_Enable(id) else go.Systray_MenuItem_Disable(id)

            // Icon
            item.iconPath?.let { iconPath ->
                runCatching {
                    val bytes = File(iconPath).takeIf { it.isFile }?.readBytes()
                    if (bytes != null) go.Systray_SetMenuItemIcon(bytes, bytes.size, id)
                }.onFailure { e -> warnln { "LinuxGoTrayManager: Failed to set menu item icon: ${e.message}" } }
            }

            // Submenu
            if (item.subMenuItems.isNotEmpty()) {
                item.subMenuItems.forEach { sub -> addMenuItemRecursive(id, sub) }
            }
        } catch (t: Throwable) {
            errorln { "LinuxGoTrayManager: Error adding menu item '${item.text}': $t" }
        }
    }
}
