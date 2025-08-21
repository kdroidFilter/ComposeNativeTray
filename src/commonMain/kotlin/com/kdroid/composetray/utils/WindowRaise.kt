package com.kdroid.composetray.utils

object WindowRaise {
    fun raise(window: java.awt.Window) {
        try {
            // Portable trick: temporarily make it topmost.
            window.isAlwaysOnTop = true
            window.toFront()
            window.requestFocus()
            window.requestFocusInWindow()
        } catch (_: Throwable) {
            // ignore
        }
    }

    fun unraise(window: java.awt.Window) {
        try { window.isAlwaysOnTop = false } catch (_: Throwable) {}
    }

    /**
     * Convenience helper to raise a window, give the window manager a brief moment,
     * then revert the temporary always-on-top flag. Useful especially on Windows.
     */
    suspend fun forceFront(window: java.awt.Window, delayMs: Long = 250) {
        // Raise first
        raise(window)
        try {
            // Give the WM a short moment to apply stacking/focus
            kotlinx.coroutines.delay(delayMs)
        } catch (_: Throwable) {
            // ignore any coroutine cancellation or other issues
        }
        // Then revert
        unraise(window)
    }
}
