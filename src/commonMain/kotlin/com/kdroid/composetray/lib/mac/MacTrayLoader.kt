package com.kdroid.composetray.lib.mac

import com.kdroid.composetray.lib.mac.MacTrayManager.MacTrayLibrary
import com.sun.jna.Native

/**
 * Centralized, process-wide loader for the macOS native tray library.
 *
 * JNA will otherwise extract the bundled dylib to a temporary, uniquely-named file
 * for each independent load request. Loading the same library multiple times can
 * lead to duplicate Objective‑C/Swift class definitions (e.g., MenuDelegate),
 * causing warnings and potential crashes on macOS.
 *
 * By exposing a single lazy-loaded instance, we ensure the library is loaded
 * exactly once per process and all callers reuse the same handle.
 */
internal object MacTrayLoader {
    val lib: MacTrayLibrary by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        Native.load("MacTray", MacTrayLibrary::class.java)
    }
}