package com.kdroid.composetray.tray.impl

import com.kdroid.composetray.lib.linux.LinuxTrayManager
import com.kdroid.composetray.lib.linux.SNIWrapper
import com.kdroid.composetray.menu.api.TrayMenuBuilder
import com.kdroid.composetray.menu.impl.LinuxTrayMenuBuilderImpl
import com.kdroid.composetray.utils.allowComposeNativeTrayLogging
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

object LinuxSNITrayInitializer {

    private var trayMenuImpl: LinuxTrayMenuBuilderImpl? = null
    private var linuxTrayManager: LinuxTrayManager? = null
    private val lock = ReentrantLock()

    init {
        val debugMode = allowComposeNativeTrayLogging
        try {
            SNIWrapper.INSTANCE.sni_set_debug_mode(if (debugMode) 1 else 0)
        } catch (e: Exception) {
        }

    }

    fun initialize(
        iconPath: String,
        tooltip: String,
        onLeftClick: (() -> Unit)? = null,
        menuContent: (TrayMenuBuilder.() -> Unit)? = null
    ) {
        lock.withLock {
            if (linuxTrayManager == null) {
                // Create a new instance of LinuxTrayManager if it doesn't exist
                linuxTrayManager = LinuxTrayManager(iconPath, tooltip, onLeftClick)

                // Create an instance of LinuxTrayMenuBuilderImpl and apply the menu content
                if (menuContent != null) {
                    trayMenuImpl = LinuxTrayMenuBuilderImpl(
                        iconPath,
                        tooltip,
                        onLeftClick,
                        trayManager = linuxTrayManager // Pass the tray manager reference
                    ).apply {
                        menuContent()
                    }
                    val menuItems = trayMenuImpl!!.build()

                    // Add each menu item to LinuxTrayManager
                    menuItems.forEach { linuxTrayManager!!.addMenuItem(it) }
                }

                // Start the Linux tray (this will initialize and run the event loop in its own thread)
                linuxTrayManager!!.startTray()
            } else {
                // Update the existing tray manager
                update(iconPath, tooltip, onLeftClick, menuContent)
            }
        }
    }

    fun update(
        iconPath: String,
        tooltip: String,
        onLeftClick: (() -> Unit)? = null,
        menuContent: (TrayMenuBuilder.() -> Unit)? = null
    ) {
        lock.withLock {
            if (linuxTrayManager == null) {
                // If tray manager doesn't exist, initialize it
                initialize(iconPath, tooltip, onLeftClick, menuContent)
                return
            }

            // Create a new menu builder and build the menu items
            val newMenuItems = if (menuContent != null) {
                val newTrayMenuImpl = LinuxTrayMenuBuilderImpl(
                    iconPath,
                    tooltip,
                    onLeftClick,
                    trayManager = linuxTrayManager
                ).apply {
                    menuContent()
                }

                // Store the new menu builder
                trayMenuImpl?.dispose()
                trayMenuImpl = newTrayMenuImpl

                // Build the menu items
                newTrayMenuImpl.build()
            } else {
                null
            }

            // Update the tray manager with new properties and menu items
            linuxTrayManager!!.update(iconPath, tooltip, onLeftClick, newMenuItems)
        }
    }

    fun dispose() {
        lock.withLock {
            // Stop the tray manager
            linuxTrayManager?.stopTray()
            linuxTrayManager = null

            // Clear menu implementation
            trayMenuImpl?.dispose()
            trayMenuImpl = null
        }
    }
}