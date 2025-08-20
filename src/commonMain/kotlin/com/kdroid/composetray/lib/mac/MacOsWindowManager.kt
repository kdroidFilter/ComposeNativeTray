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

    companion object {
        // Constants for NSApplication activation policies
        const val NSApplicationActivationPolicyRegular = 0L
        const val NSApplicationActivationPolicyAccessory = 1L
        const val NSApplicationActivationPolicyProhibited = 2L

        // Constants for window levels
        const val NSNormalWindowLevel = 0L
        const val NSFloatingWindowLevel = 3L
        const val NSModalPanelWindowLevel = 8L
    }

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

}

