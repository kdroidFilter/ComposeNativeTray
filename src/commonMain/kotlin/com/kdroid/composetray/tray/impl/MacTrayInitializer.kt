package com.kdroid.composetray.tray.impl

import com.kdroid.composetray.lib.mac.MacTrayManager
import com.kdroid.composetray.menu.api.TrayMenuBuilder
import com.kdroid.composetray.menu.impl.MacTrayMenuBuilderImpl

object MacTrayInitializer {

    private var trayMenuImpl: MacTrayMenuBuilderImpl? = null
    private var macTrayManager: MacTrayManager? = null
    private val lock = Object()

    fun initialize(iconPath: String, tooltip: String, onLeftClick: (() -> Unit)? = null, menuContent: (TrayMenuBuilder.() -> Unit)? = null) {
        synchronized(lock) {
            if (macTrayManager == null) {
                // Create a new instance of MacTrayManager if it doesn't exist
                macTrayManager = MacTrayManager(iconPath, tooltip, onLeftClick)
                
                // Create an instance of MacTrayMenuBuilderImpl and apply the menu content
                if (menuContent != null) {
                    trayMenuImpl = MacTrayMenuBuilderImpl(
                        iconPath,
                        tooltip,
                        onLeftClick,
                        trayManager = macTrayManager // Pass the tray manager reference
                    ).apply {
                        menuContent()
                    }
                    val menuItems = trayMenuImpl!!.build()

                    // Add each menu item to MacTrayManager
                    menuItems.forEach { macTrayManager!!.addMenuItem(it) }
                }

                // Start the macOS tray (this will create its own thread)
                macTrayManager!!.startTray()
            } else {
                // Update the existing tray manager
                update(iconPath, tooltip, onLeftClick, menuContent)
            }
        }
    }
    
    fun update(iconPath: String, tooltip: String, onLeftClick: (() -> Unit)? = null, menuContent: (TrayMenuBuilder.() -> Unit)? = null) {
        synchronized(lock) {
            if (macTrayManager == null) {
                // If tray manager doesn't exist, initialize it
                initialize(iconPath, tooltip, onLeftClick, menuContent)
                return
            }
            
            // Create a new menu builder and build the menu items
            val newMenuItems = if (menuContent != null) {
                val newTrayMenuImpl = MacTrayMenuBuilderImpl(
                    iconPath,
                    tooltip,
                    onLeftClick,
                    trayManager = macTrayManager
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
            macTrayManager!!.update(iconPath, tooltip, onLeftClick, newMenuItems)
        }
    }

    fun dispose() {
        synchronized(lock) {
            // Stop the tray manager
            macTrayManager?.stopTray()
            macTrayManager = null

            // Clear menu implementation
            trayMenuImpl?.dispose()
            trayMenuImpl = null
        }
    }
}