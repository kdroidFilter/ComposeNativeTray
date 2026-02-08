package com.kdroid.composetray.lib.mac

import com.kdroid.composetray.utils.debugln
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import io.github.kdroidfilter.platformtools.OperatingSystem
import io.github.kdroidfilter.platformtools.getOperatingSystem

// JNA interface to access the Objective-C runtime
interface ObjectiveC : Library {
    fun objc_msgSend(receiver: Pointer, selector: Pointer, vararg args: Any): Pointer
    fun objc_msgSend(receiver: Pointer, selector: Pointer): Pointer
    fun objc_msgSend(receiver: Pointer, selector: Pointer, arg: Long): Pointer
    fun sel_registerName(name: String): Pointer
    fun objc_getClass(name: String): Pointer

    companion object {
        val INSTANCE: ObjectiveC = Native.load("objc", ObjectiveC::class.java)
    }
}

// Interface for Foundation framework
interface Foundation : Library {
    companion object {
        val INSTANCE: Foundation = Native.load("Foundation", Foundation::class.java)
    }
}

class MacOSWindowManager {

    // Detect platform once
    private val isMacOs: Boolean = getOperatingSystem() == OperatingSystem.MACOS


    // Load Objective-C runtime only on macOS and only when needed
    private val objc: ObjectiveC? by lazy {
        if (!isMacOs) return@lazy null
        try {
            ObjectiveC.INSTANCE
        } catch (t: Throwable) {
            debugln { "Failed to load Objective-C runtime: ${t.message}" }
            null
        }
    }

    private var nsApplicationInstance: Pointer? = null

    /**
     * Initialize NSApplication if not already done
     */
    private fun ensureNSApplicationInitialized(): Boolean {
        if (!isMacOs) {
            // Not on macOS: pretend not initialized, but avoid side effects
            return false
        }

        if (nsApplicationInstance != null && nsApplicationInstance != Pointer.NULL) {
            return true
        }

        val localObjc = objc ?: return false

        return try {
            debugln { "Initializing NSApplication..." }

            // Get NSApplication class
            val nsApplicationClass = localObjc.objc_getClass("NSApplication")
            if (nsApplicationClass == Pointer.NULL) {
                debugln { "Unable to get NSApplication class" }
                return false
            }

            // Get selector for sharedApplication
            val sharedApplicationSelector = localObjc.sel_registerName("sharedApplication")
            if (sharedApplicationSelector == Pointer.NULL) {
                debugln { "Unable to get sharedApplication selector" }
                return false
            }

            // Call [NSApplication sharedApplication]
            nsApplicationInstance = localObjc.objc_msgSend(nsApplicationClass, sharedApplicationSelector)
            if (nsApplicationInstance == Pointer.NULL) {
                debugln { "Unable to get NSApplication instance" }
                nsApplicationInstance = null
                return false
            }

            debugln { "NSApplication initialized successfully" }
            true

        } catch (e: Exception) {
            debugln { "Error while initializing NSApplication: ${e.message}" }
            e.printStackTrace()
            nsApplicationInstance = null
            false
        }
    }

    /**
     * Get the shared NSApplication instance via the Objective-C runtime
     */
    private fun getNSApplication(): Pointer? {
        if (!ensureNSApplicationInitialized()) {
            return null
        }
        return nsApplicationInstance
    }

    /**
     * Show the application in the Dock
     */
    fun showInDock(): Boolean {
        if (!isMacOs) {
            // No-op on non-macOS
            debugln { "showInDock(): non-macOS platform detected, no action performed" }
            return true
        }
        val localObjc = objc ?: return false
        return try {
            val nsApp = getNSApplication()
            if (nsApp == null) {
                debugln { "NSApplication not available" }
                return false
            }

            val setActivationPolicySelector = localObjc.sel_registerName("setActivationPolicy:")
            if (setActivationPolicySelector == Pointer.NULL) {
                debugln { "Unable to get setActivationPolicy: selector" }
                return false
            }

            // Restore normal policy
            localObjc.objc_msgSend(
                nsApp,
                setActivationPolicySelector,
                NSApplicationActivationPolicyRegular
            )

            debugln { "Application restored in the Dock" }
            true

        } catch (e: Exception) {
            debugln { "Error while restoring in Dock: ${e.message}" }
            e.printStackTrace()
            false
        }
    }

    /**
     * Set the application as accessory (no Dock icon, but can have a menu)
     */
    fun hideFromDock(): Boolean {
        if (!isMacOs) {
            // No-op on non-macOS
            debugln { "hideFromDock(): non-macOS platform detected, no action performed" }
            return true
        }
        val localObjc = objc ?: return false
        return try {
            val nsApp = getNSApplication()
            if (nsApp == null) {
                debugln { "NSApplication not available" }
                return false
            }

            val setActivationPolicySelector = localObjc.sel_registerName("setActivationPolicy:")
            if (setActivationPolicySelector == Pointer.NULL) {
                debugln { "Unable to get setActivationPolicy: selector" }
                return false
            }

            localObjc.objc_msgSend(
                nsApp,
                setActivationPolicySelector,
                NSApplicationActivationPolicyAccessory
            )

            debugln { "Application set as accessory" }
            true

        } catch (e: Exception) {
            debugln { "Error while setting as accessory: ${e.message}" }
            e.printStackTrace()
            false
        }
    }

    /**
     * Check if the application can be hidden from the Dock
     */
    fun canHideFromDock(): Boolean {
        if (!isMacOs) return false
        return getNSApplication() != null
    }

    /**
     * Configure an AWT window so that macOS moves it to the active Space
     * when it is ordered front, instead of switching back to the Space
     * where the window was originally created.
     */
    fun setMoveToActiveSpace(awtWindow: java.awt.Window): Boolean {
        if (!isMacOs) return false
        val localObjc = objc ?: return false
        return try {
            // Try direct approach via Native.getComponentID
            val viewPtr = Native.getComponentID(awtWindow)
            debugln { "[MacOSWindowManager] setMoveToActiveSpace: viewPtr=$viewPtr" }
            if (viewPtr != 0L) {
                val nsView = Pointer(viewPtr)
                val windowSel = localObjc.sel_registerName("window")
                val nsWindow = localObjc.objc_msgSend(nsView, windowSel)
                if (nsWindow != Pointer.NULL) {
                    applySpaceBehavior(localObjc, nsWindow)
                    return true
                }
            }

            // Fallback: iterate NSApp windows and set on floating-level windows
            // (tray popup uses alwaysOnTop=true which sets a floating window level)
            debugln { "[MacOSWindowManager] Fallback: searching NSApp windows for floating window..." }
            val nsApp = getNSApplication() ?: return false

            val windowsSel = localObjc.sel_registerName("windows")
            val windowsArray = localObjc.objc_msgSend(nsApp, windowsSel)
            val countSel = localObjc.sel_registerName("count")
            val count = Pointer.nativeValue(localObjc.objc_msgSend(windowsArray, countSel)).toInt()
            debugln { "[MacOSWindowManager] Found $count NSWindows" }

            val objectAtIndexSel = localObjc.sel_registerName("objectAtIndex:")
            val levelSel = localObjc.sel_registerName("level")

            var applied = false
            for (i in 0 until count) {
                val nsWindow = localObjc.objc_msgSend(windowsArray, objectAtIndexSel, i.toLong())
                val level = Pointer.nativeValue(localObjc.objc_msgSend(nsWindow, levelSel))
                debugln { "[MacOSWindowManager] Window[$i]: level=$level" }
                // Floating windows have level > 0 (NSFloatingWindowLevel = 3)
                if (level > 0) {
                    applySpaceBehavior(localObjc, nsWindow)
                    applied = true
                }
            }
            applied
        } catch (e: Throwable) {
            debugln { "Failed to set moveToActiveSpace: ${e.message}" }
            false
        }
    }

    private fun applySpaceBehavior(localObjc: ObjectiveC, nsWindow: Pointer) {
        val getCollSel = localObjc.sel_registerName("collectionBehavior")
        val current = Pointer.nativeValue(localObjc.objc_msgSend(nsWindow, getCollSel))
        // Ensure moveToActiveSpace is set (moves window to active Space when ordered front)
        val desired = (current and NSWindowCollectionBehaviorCanJoinAllSpaces.inv()) or NSWindowCollectionBehaviorMoveToActiveSpace
        if (current != desired) {
            debugln { "[MacOSWindowManager] collectionBehavior before=$current, desired=$desired" }
            val setCollSel = localObjc.sel_registerName("setCollectionBehavior:")
            localObjc.objc_msgSend(nsWindow, setCollSel, desired)
            val after = Pointer.nativeValue(localObjc.objc_msgSend(nsWindow, getCollSel))
            debugln { "[MacOSWindowManager] collectionBehavior after=$after" }
        }
        debugln { "Window configured with moveToActiveSpace" }
    }

    /**
     * Check if any floating-level NSWindow is on the active Space.
     * Uses NSApp.windows iteration (same fallback as setMoveToActiveSpace).
     * Returns true if on active Space or if check fails (fail-open).
     */
    fun isFloatingWindowOnActiveSpace(): Boolean {
        if (!isMacOs) return true
        val localObjc = objc ?: return true
        return try {
            val nsApp = getNSApplication() ?: return true

            val windowsSel = localObjc.sel_registerName("windows")
            val windowsArray = localObjc.objc_msgSend(nsApp, windowsSel)
            val countSel = localObjc.sel_registerName("count")
            val count = Pointer.nativeValue(localObjc.objc_msgSend(windowsArray, countSel)).toInt()

            val objectAtIndexSel = localObjc.sel_registerName("objectAtIndex:")
            val levelSel = localObjc.sel_registerName("level")
            val isOnActiveSpaceSel = localObjc.sel_registerName("isOnActiveSpace")

            for (i in 0 until count) {
                val nsWindow = localObjc.objc_msgSend(windowsArray, objectAtIndexSel, i.toLong())
                val level = Pointer.nativeValue(localObjc.objc_msgSend(nsWindow, levelSel))
                if (level > 0) {
                    val onActiveSpace = Pointer.nativeValue(localObjc.objc_msgSend(nsWindow, isOnActiveSpaceSel)) != 0L
                    debugln { "[MacOSWindowManager] Floating window level=$level, isOnActiveSpace=$onActiveSpace" }
                    return onActiveSpace
                }
            }
            true // No floating window found, assume on active Space
        } catch (e: Throwable) {
            debugln { "Failed to check isOnActiveSpace: ${e.message}" }
            true
        }
    }

    /**
     * Check if an AWT window is currently on the active macOS Space.
     * Returns true if on the active Space, false if on another Space.
     * Returns true by default if the check cannot be performed (fail-open).
     */
    fun isOnActiveSpace(awtWindow: java.awt.Window): Boolean {
        if (!isMacOs) return true
        val localObjc = objc ?: return true
        return try {
            val viewPtr = Native.getComponentID(awtWindow)
            if (viewPtr == 0L) return true

            val nsView = Pointer(viewPtr)
            val windowSel = localObjc.sel_registerName("window")
            val nsWindow = localObjc.objc_msgSend(nsView, windowSel)
            if (nsWindow == Pointer.NULL) return true

            val isOnActiveSpaceSel = localObjc.sel_registerName("isOnActiveSpace")
            val result = Pointer.nativeValue(localObjc.objc_msgSend(nsWindow, isOnActiveSpaceSel))
            result != 0L
        } catch (e: Throwable) {
            debugln { "Failed to check isOnActiveSpace: ${e.message}" }
            true // fail-open: assume on active Space
        }
    }

    /**
     * Bring the floating-level NSWindow to the front on the active Space.
     * With moveToActiveSpace collection behavior, this physically moves the window.
     * Also activates the application to ensure focus is gained.
     */
    fun bringFloatingWindowToFront(): Boolean {
        if (!isMacOs) return false
        val localObjc = objc ?: return false
        return try {
            val nsApp = getNSApplication() ?: return false

            // Activate the app so it can receive focus
            val activateSel = localObjc.sel_registerName("activateIgnoringOtherApps:")
            localObjc.objc_msgSend(nsApp, activateSel, 1L)

            val windowsSel = localObjc.sel_registerName("windows")
            val windowsArray = localObjc.objc_msgSend(nsApp, windowsSel)
            val countSel = localObjc.sel_registerName("count")
            val count = Pointer.nativeValue(localObjc.objc_msgSend(windowsArray, countSel)).toInt()

            val objectAtIndexSel = localObjc.sel_registerName("objectAtIndex:")
            val levelSel = localObjc.sel_registerName("level")
            val makeKeyAndOrderFrontSel = localObjc.sel_registerName("makeKeyAndOrderFront:")

            for (i in 0 until count) {
                val nsWindow = localObjc.objc_msgSend(windowsArray, objectAtIndexSel, i.toLong())
                val level = Pointer.nativeValue(localObjc.objc_msgSend(nsWindow, levelSel))
                if (level > 0) {
                    debugln { "[MacOSWindowManager] bringFloatingWindowToFront: level=$level" }
                    localObjc.objc_msgSend(nsWindow, makeKeyAndOrderFrontSel, Pointer.NULL)
                    return true
                }
            }
            false
        } catch (e: Throwable) {
            debugln { "Failed to bringFloatingWindowToFront: ${e.message}" }
            false
        }
    }

    companion object {
        // Constants for NSApplication activation policies
        const val NSApplicationActivationPolicyRegular = 0L
        const val NSApplicationActivationPolicyAccessory = 1L
        const val NSApplicationActivationPolicyProhibited = 2L

        // Constants for window levels
        const val NSNormalWindowLevel = 0L
        const val NSFloatingWindowLevel = 3L
        const val NSModalPanelWindowLevel = 8L

        // NSWindowCollectionBehavior
        const val NSWindowCollectionBehaviorCanJoinAllSpaces = 1L // 1 << 0
        const val NSWindowCollectionBehaviorMoveToActiveSpace = 2L // 1 << 1
    }

}

