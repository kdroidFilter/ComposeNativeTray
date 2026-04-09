package com.kdroid.composetray.lib.mac

import com.kdroid.composetray.utils.extractToTempIfDifferent
import java.io.File

/**
 * JNI bridge to the native macOS tray library (libMacTray.dylib).
 * Replaces the previous JNA direct-mapping approach.
 * All methods are static JNI calls into MacTrayBridge.m.
 */
internal object MacNativeBridge {

    init {
        loadNativeLibrary()
    }

    private fun loadNativeLibrary() {
        val arch = System.getProperty("os.arch") ?: "aarch64"
        val resourceDir = when {
            arch.contains("aarch64") || arch.contains("arm64") -> "darwin-aarch64"
            else -> "darwin-x86-64"
        }
        val resourcePath = "composetray/native/$resourceDir/libMacTray.dylib"

        // Try to find the dylib on the classpath (inside a JAR or on disk)
        val url = MacNativeBridge::class.java.classLoader?.getResource(resourcePath)
        if (url != null) {
            val protocol = url.protocol
            if (protocol == "jar") {
                // Extract from JAR to a temp file
                val tempFile = extractToTempIfDifferent(url.toString())
                if (tempFile != null) {
                    System.load(tempFile.absolutePath)
                    return
                }
            } else if (protocol == "file") {
                // Direct file on disk (development mode)
                val file = File(url.toURI())
                if (file.exists()) {
                    System.load(file.absolutePath)
                    return
                }
            }
        }

        // Fallback: let the JVM find it on java.library.path
        System.loadLibrary("MacTray")
    }

    // ── Tray lifecycle ──────────────────────────────────────────────────

    @JvmStatic external fun nativeCreateTray(iconPath: String, tooltip: String): Long
    @JvmStatic external fun nativeFreeTray(handle: Long)
    @JvmStatic external fun nativeSetTrayIcon(handle: Long, iconPath: String)
    @JvmStatic external fun nativeSetTrayTooltip(handle: Long, tooltip: String)
    @JvmStatic external fun nativeSetTrayCallback(handle: Long, callback: Runnable?)
    @JvmStatic external fun nativeSetTrayMenu(trayHandle: Long, menuHandle: Long)
    @JvmStatic external fun nativeClearTrayMenu(trayHandle: Long)
    @JvmStatic external fun nativeInitTray(handle: Long): Int
    @JvmStatic external fun nativeLoopTray(blocking: Int): Int
    @JvmStatic external fun nativeUpdateTray(handle: Long)
    @JvmStatic external fun nativeDisposeTray(handle: Long)
    @JvmStatic external fun nativeExitTray()

    // ── Menu items ──────────────────────────────────────────────────────

    @JvmStatic external fun nativeCreateMenuItems(count: Int): Long
    @JvmStatic external fun nativeSetMenuItem(
        menuHandle: Long, index: Int,
        text: String, iconPath: String?,
        disabled: Int, checked: Int
    )
    @JvmStatic external fun nativeSetMenuItemCallback(menuHandle: Long, index: Int, callback: Runnable?)
    @JvmStatic external fun nativeSetMenuItemSubmenu(menuHandle: Long, index: Int, submenuHandle: Long)
    @JvmStatic external fun nativeFreeMenuItems(menuHandle: Long, count: Int)

    // ── Theme ───────────────────────────────────────────────────────────

    @JvmStatic external fun nativeSetThemeCallback(callback: ThemeChangeCallback?)
    @JvmStatic external fun nativeIsMenuDark(): Int

    // ── Position ────────────────────────────────────────────────────────

    /** Writes [x, y] into outXY. Returns 1 if precise, 0 if fallback. */
    @JvmStatic external fun nativeGetStatusItemPosition(outXY: IntArray): Int
    @JvmStatic external fun nativeGetStatusItemRegion(): String
    @JvmStatic external fun nativeGetStatusItemPositionFor(handle: Long, outXY: IntArray): Int
    @JvmStatic external fun nativeGetStatusItemRegionFor(handle: Long): String

    // ── Appearance ──────────────────────────────────────────────────────

    @JvmStatic external fun nativeSetIconsForAppearance(handle: Long, lightIcon: String, darkIcon: String)

    // ── Window management ───────────────────────────────────────────────

    @JvmStatic external fun nativeShowInDock(): Int
    @JvmStatic external fun nativeHideFromDock(): Int
    @JvmStatic external fun nativeSetMoveToActiveSpace()
    @JvmStatic external fun nativeSetMoveToActiveSpaceForWindow(viewPtr: Long): Int
    @JvmStatic external fun nativeIsFloatingWindowOnActiveSpace(): Int
    @JvmStatic external fun nativeBringFloatingWindowToFront(): Int
    @JvmStatic external fun nativeIsOnActiveSpaceForView(viewPtr: Long): Int

    // ── Mouse ───────────────────────────────────────────────────────────

    @JvmStatic external fun nativeGetMouseButtonState(button: Int): Int

    // ── JAWT ────────────────────────────────────────────────────────────

    /** Returns the NSView pointer for an AWT component, or 0 on failure. */
    @JvmStatic external fun nativeGetAWTViewPtr(awtComponent: Any): Long

    // ── Callback interface ──────────────────────────────────────────────

    interface ThemeChangeCallback {
        fun onThemeChanged(isDark: Int)
    }
}
