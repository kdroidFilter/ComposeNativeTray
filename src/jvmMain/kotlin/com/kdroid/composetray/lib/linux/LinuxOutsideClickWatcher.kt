package com.kdroid.composetray.lib.linux

import com.kdroid.composetray.utils.isPointWithinLinuxStatusItem
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.NativeLong
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.NativeLongByReference
import io.github.kdroidfilter.platformtools.OperatingSystem
import io.github.kdroidfilter.platformtools.getOperatingSystem
import java.awt.Window
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Minimal X11 binding for what we need.
 */
internal interface X11 : Library {
    companion object {
        val INSTANCE: X11 = Native.load("X11", X11::class.java)
    }

    fun XOpenDisplay(displayName: String?): Pointer?
    fun XDefaultRootWindow(display: Pointer?): NativeLong
    fun XQueryPointer(
        display: Pointer?,
        w: NativeLong,
        root_return: NativeLongByReference,
        child_return: NativeLongByReference,
        root_x_return: IntByReference,
        root_y_return: IntByReference,
        win_x_return: IntByReference,
        win_y_return: IntByReference,
        mask_return: IntByReference
    ): Int

    fun XCloseDisplay(display: Pointer?): Int
}

/**
 * LinuxOutsideClickWatcher: X11/XWayland implementation that detects a left-click anywhere,
 * and if it is outside the supplied window (and not on the tray icon area), invokes a callback.
 *
 * Notes:
 * - Requires X11/XWayland (DISPLAY must be set). Will no-op on Wayland-only sessions without XWayland.
 * - Polls with XQueryPointer at ~60 Hz, reading Button1Mask for "left pressed".
 */
class LinuxOutsideClickWatcher(
    private val windowSupplier: () -> Window?,
    private val onOutsideClick: () -> Unit
) : AutoCloseable {

    private var scheduler: ScheduledExecutorService? = null
    private var prevLeft = false

    // X11 state
    private var display: Pointer? = null
    private var rootWindow: NativeLong = NativeLong(0)

    fun start() {
        if (getOperatingSystem() != OperatingSystem.LINUX) return
        if (scheduler != null) return

        try {
            val x11 = X11.INSTANCE
            display = x11.XOpenDisplay(null)
            if (display == null) {
                // No X11 available (e.g., Wayland-only session) -> do nothing.
                return
            }
            rootWindow = x11.XDefaultRootWindow(display)
        } catch (_: Throwable) {
            // Failed to connect to X11 -> do nothing.
            display = null
            return
        }

        scheduler = Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "LinuxOutsideClickWatcher").apply { isDaemon = true }
        }.also { exec ->
            exec.scheduleAtFixedRate({ pollOnce() }, 0, 16, TimeUnit.MILLISECONDS)
        }
    }

    private fun pollOnce() {
        val dpy = display ?: return
        try {
            val x11 = X11.INSTANCE

            val rootRet = NativeLongByReference()
            val childRet = NativeLongByReference()
            val rootX = IntByReference()
            val rootY = IntByReference()
            val winX = IntByReference()
            val winY = IntByReference()
            val mask = IntByReference()

            // Bool return (0 = False, non-zero = True)
            val ok = x11.XQueryPointer(
                dpy,
                rootWindow,
                rootRet,
                childRet,
                rootX,
                rootY,
                winX,
                winY,
                mask
            ) != 0

            if (!ok) return

            val left = (mask.value and BUTTON1_MASK) != 0

            // Rising edge: only act once when the button goes down
            if (left && left != prevLeft) {
                val win = windowSupplier.invoke()
                if (win != null && win.isShowing) {
                    val px = rootX.value
                    val py = rootY.value

                    val winLoc = try { win.locationOnScreen } catch (_: Throwable) { null }
                    if (winLoc != null) {
                        val wx = winLoc.x
                        val wy = winLoc.y
                        val ww = win.width
                        val wh = win.height

                        val insideWindow = px >= wx && px < wx + ww && py >= wy && py < wy + wh
                        val onTrayIcon = try { isPointWithinLinuxStatusItem(px, py) } catch (_: Throwable) { false }

                        if (!insideWindow && !onTrayIcon) {
                            onOutsideClick.invoke()
                        }
                    }
                }
            }

            prevLeft = left
        } catch (_: Throwable) {
            // Swallow errors to keep the scheduler alive
        }
    }

    fun stop() = close()

    override fun close() {
        try { scheduler?.shutdownNow() } catch (_: Throwable) {}
        scheduler = null

        // Close X11 display after stopping the poller
        try {
            display?.let { X11.INSTANCE.XCloseDisplay(it) }
        } catch (_: Throwable) {
            // ignore
        } finally {
            display = null
            rootWindow = NativeLong(0)
        }
    }

    private companion object {
        // X11 button mask bit for Button1 (left)
        private const val BUTTON1_MASK = 1 shl 8
    }
}
