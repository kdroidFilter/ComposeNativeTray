package com.kdroid.composetray.lib.mac

import com.kdroid.composetray.lib.mac.MacTrayManager.MacTrayLibrary

/**
 * Centralized, process-wide loader for the macOS native tray library.
 *
 * With JNA direct mapping, registration happens inside MacTrayLibrary's init block.
 * We keep this object to preserve API shape if referenced elsewhere, but simply return
 * the singleton object so callers can keep using MacTrayLoader.lib if needed.
 */
internal object MacTrayLoader {
    val lib: MacTrayLibrary by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        MacTrayLibrary
    }
}