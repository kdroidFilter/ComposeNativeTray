package com.kdroid.composetray.lib.windows

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Structure
import io.github.kdroidfilter.platformtools.OperatingSystem
import io.github.kdroidfilter.platformtools.getOperatingSystem
import java.awt.Window
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Minimal JNA mapping for user32.dll functions we need.
 */
interface User32 : Library {
    companion object {
        val INSTANCE: User32 = Native.load("user32", User32::class.java)
        const val VK_LBUTTON = 0x01
    }

    /**
     * Returns the state of a virtual key. High-order bit set => key is down.
     */
    fun GetAsyncKeyState(vKey: Int): Short

    /**
     * Retrieves the cursor's position, in screen coordinates.
     * Returns true on success; fills the provided POINT.
     */
    fun GetCursorPos(lpPoint: POINT): Boolean
}

/**
 * Win32 POINT structure.
 */
open class POINT : Structure() {
    @JvmField var x: Int = 0
    @JvmField var y: Int = 0
    override fun getFieldOrder() = listOf("x", "y")
}

/**
 * WindowsOutsideClickWatcher: polls the left mouse button state and cursor position.
 * When a left-click press is detected (transition from up -> down) outside the target window,
 * it invokes onOutsideClick(). You can provide an optional ignore predicate (e.g., to ignore
 * clicks on the system tray icon) that receives (x, y) screen coordinates and returns true
 * if the click should be ignored.
 */
class WindowsOutsideClickWatcher(
    private val windowSupplier: () -> Window?,
    private val onOutsideClick: () -> Unit,
    private val ignorePointPredicate: ((x: Int, y: Int) -> Boolean)? = null
) : AutoCloseable {

    private var scheduler: ScheduledExecutorService? = null
    private var prevLeftDown: Boolean = false

    fun start() {
        if (getOperatingSystem() != OperatingSystem.WINDOWS) return
        if (scheduler != null) return

        scheduler = Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "WindowsOutsideClickWatcher").apply { isDaemon = true }
        }.also { exec ->
            // ~60Hz polling; tune if you need lower CPU usage
            exec.scheduleAtFixedRate({ pollOnce() }, 0, 16, TimeUnit.MILLISECONDS)
        }
    }

    private fun pollOnce() {
        try {
            val u32 = User32.INSTANCE
            // High-order bit set => key is pressed
            val leftDownNow = (u32.GetAsyncKeyState(User32.VK_LBUTTON).toInt() and 0x8000) != 0

            // Fire only on the transition from "not pressed" to "pressed"
            if (leftDownNow && !prevLeftDown) {
                val win = windowSupplier.invoke()
                if (win != null && win.isShowing) {
                    val pt = POINT()
                    if (u32.GetCursorPos(pt)) {
                        val px = pt.x
                        val py = pt.y

                        val winLoc = try { win.locationOnScreen } catch (_: Throwable) { null }
                        if (winLoc != null) {
                            val wx = winLoc.x
                            val wy = winLoc.y
                            val ww = win.width
                            val wh = win.height

                            val insideWindow =
                                px >= wx && px < wx + ww && py >= wy && py < wy + wh

                            val ignored = ignorePointPredicate?.invoke(px, py) == true

                            if (!insideWindow && !ignored) {
                                onOutsideClick.invoke()
                            }
                        }
                    }
                }
            }

            prevLeftDown = leftDownNow
        } catch (_: Throwable) {
            // Swallow errors to keep the scheduler alive
        }
    }

    fun stop() = close()

    override fun close() {
        try {
            scheduler?.shutdownNow()
        } catch (_: Throwable) {
        } finally {
            scheduler = null
        }
    }
}
