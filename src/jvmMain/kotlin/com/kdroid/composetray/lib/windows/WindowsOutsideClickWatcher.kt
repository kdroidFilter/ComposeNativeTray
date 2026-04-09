package com.kdroid.composetray.lib.windows

import com.sun.jna.Pointer
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinUser
import com.sun.jna.platform.win32.User32
import io.github.kdroidfilter.platformtools.OperatingSystem
import io.github.kdroidfilter.platformtools.getOperatingSystem
import java.awt.Window
import java.util.concurrent.atomic.AtomicBoolean

/**
 * WindowsOutsideClickWatcher using a low-level mouse hook (WH_MOUSE_LL).
 *
 * Behavior:
 *  - Listens for global left-button *down* events.
 *  - If the click occurs outside the supplied window (and not ignored by predicate), invokes onOutsideClick().
 *
 * Public signatures are preserved.
 */
class WindowsOutsideClickWatcher(
    private val windowSupplier: () -> Window?,
    private val onOutsideClick: () -> Unit,
    private val ignorePointPredicate: ((x: Int, y: Int) -> Boolean)? = null
) : AutoCloseable {

    @Volatile private var hookThread: Thread? = null
    @Volatile private var hookHandle: WinUser.HHOOK? = null
    @Volatile private var hookThreadId: Int = 0
    @Volatile private var mouseProc: WinUser.LowLevelMouseProc? = null
    private val stopping = AtomicBoolean(false)

    private companion object {
        const val WH_MOUSE_LL = 14
        const val WM_LBUTTONDOWN = 0x0201
        const val WM_NCLBUTTONDOWN = 0x00A1
        const val WM_QUIT = 0x0012
    }

    /** Start the global low-level mouse hook on a dedicated daemon thread. */
    fun start() {
        if (getOperatingSystem() != OperatingSystem.WINDOWS) return
        synchronized(this) {
            if (hookThread != null) return
            stopping.set(false)

            hookThread = Thread({
                hookThreadId = Kernel32.INSTANCE.GetCurrentThreadId()

                // Strong reference kept on the field to avoid GC of the callback.
                mouseProc = WinUser.LowLevelMouseProc { nCode, wParam, lParam ->
                    try {
                        if (nCode >= 0) {
                            val msg = wParam.toInt() // For WH_MOUSE_LL this is the WM_* code.
                            if (msg == WM_LBUTTONDOWN || msg == WM_NCLBUTTONDOWN) {
                                // lParam is already the populated MSLLHOOKSTRUCT.
                                val px = lParam.pt.x
                                val py = lParam.pt.y

                                val win = windowSupplier()
                                if (win != null && win.isShowing) {
                                    val winLoc = try { win.locationOnScreen } catch (_: Throwable) { null }
                                    if (winLoc != null) {
                                        // Get the graphics configuration to determine the DPI scale
                                        val scale = try {
                                            win.graphicsConfiguration?.defaultTransform?.scaleX ?: 1.0
                                        } catch (_: Throwable) { 1.0 }

                                        // Convert window bounds from logical to physical pixels
                                        val wx = (winLoc.x * scale).toInt()
                                        val wy = (winLoc.y * scale).toInt()
                                        val ww = (win.width * scale).toInt()
                                        val wh = (win.height * scale).toInt()

                                        val insideWindow = px in wx until (wx + ww) && py in wy until (wy + wh)
                                        val ignored = ignorePointPredicate?.invoke(px, py) == true

                                        if (!insideWindow && !ignored) {
                                            // Let caller decide EDT marshaling.
                                            onOutsideClick()
                                        }
                                    }
                                }
                            }
                        }
                    } catch (_: Throwable) {
                        // Never crash the hook; always fall through to CallNextHookEx.
                    }

                    // Pass original WPARAM and a *pointer* to the struct as LPARAM.
                    val lParamNative = WinDef.LPARAM(Pointer.nativeValue(lParam.pointer))
                    User32.INSTANCE.CallNextHookEx(hookHandle, nCode, wParam, lParamNative)
                }

                // Install the hook (global, threadId = 0).
                val hMod = Kernel32.INSTANCE.GetModuleHandle(null)
                hookHandle = User32.INSTANCE.SetWindowsHookEx(WH_MOUSE_LL, mouseProc, hMod, 0)

                if (hookHandle == null) {
                    mouseProc = null
                    return@Thread
                }

                // Minimal message loop to keep the hook thread alive.
                val msg = WinUser.MSG()
                while (!stopping.get()) {
                    val r = User32.INSTANCE.GetMessage(msg, null, 0, 0)
                    if (r == 0 || r == -1) break // WM_QUIT or error
                }

                // Cleanup before thread exits.
                try {
                    hookHandle?.let { User32.INSTANCE.UnhookWindowsHookEx(it) }
                } finally {
                    hookHandle = null
                    mouseProc = null
                }
            }, "WindowsOutsideClickWatcher-LL").apply {
                isDaemon = true
                start()
            }
        }
    }

    /** Stop the hook (alias to close()). */
    fun stop() = close()

    /** Uninstalls the hook and signals the hook thread to exit. */
    override fun close() {
        synchronized(this) {
            stopping.set(true)

            // Unhook immediately; also helps release if thread is blocked in GetMessage().
            try {
                hookHandle?.let { User32.INSTANCE.UnhookWindowsHookEx(it) }
            } catch (_: Throwable) {
            } finally {
                hookHandle = null
            }

            // Break GetMessage() with WM_QUIT.
            if (hookThreadId != 0) {
                try {
                    User32.INSTANCE.PostThreadMessage(
                        hookThreadId,
                        WM_QUIT,
                        WinDef.WPARAM(0),
                        WinDef.LPARAM(0)
                    )
                } catch (_: Throwable) {
                }
            }

            hookThread = null
            hookThreadId = 0
            mouseProc = null
        }
    }
}
