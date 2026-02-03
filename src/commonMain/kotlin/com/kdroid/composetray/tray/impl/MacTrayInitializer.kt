package com.kdroid.composetray.tray.impl

import com.kdroid.composetray.lib.mac.MacTrayManager
import com.kdroid.composetray.menu.api.TrayMenuBuilder
import com.kdroid.composetray.menu.impl.MacTrayMenuBuilderImpl

object MacTrayInitializer {

    private const val DEFAULT_ID: String = "_default"

    // Manage multiple tray managers and builders by ID
    private val trayManagers: MutableMap<String, MacTrayManager> = mutableMapOf()
    private val trayMenuBuilders: MutableMap<String, MacTrayMenuBuilderImpl> = mutableMapOf()

    // Accessors for per-instance integration
    @Synchronized
    internal fun getManager(id: String): MacTrayManager? = trayManagers[id]

    @Synchronized
    internal fun getNativeTrayStruct(id: String): MacTrayManager.MacTray? = trayManagers[id]?.getNativeTrayStruct()

    @Synchronized
    fun initialize(
        id: String,
        iconPath: String,
        tooltip: String,
        onLeftClick: (() -> Unit)? = null,
        menuContent: (TrayMenuBuilder.() -> Unit)? = null
    ) {
        var manager = trayManagers[id]
        if (manager == null) {
            // Create a new manager for this ID
            manager = MacTrayManager(iconPath, tooltip, onLeftClick)
            trayManagers[id] = manager

            // Build menu for this manager
            val builder = MacTrayMenuBuilderImpl(
                iconPath,
                tooltip,
                onLeftClick,
                trayManager = manager
            ).apply {
                menuContent?.invoke(this)
            }
            // Replace old builder for this ID if any
            trayMenuBuilders.remove(id)?.dispose()
            trayMenuBuilders[id] = builder

            val menuItems = builder.build()
            // Add each built item to manager before starting
            menuItems.forEach { manager.addMenuItem(it) }

            // Start the macOS tray for this manager
            manager.startTray()
        } else {
            // Existing manager: delegate to update with the provided content
            update(id, iconPath, tooltip, onLeftClick, menuContent)
        }
    }

    @Synchronized
    fun update(
        id: String,
        iconPath: String,
        tooltip: String,
        onLeftClick: (() -> Unit)? = null,
        menuContent: (TrayMenuBuilder.() -> Unit)? = null
    ) {
        val manager = trayManagers[id]
        if (manager == null) {
            // If manager doesn't exist, initialize it
            initialize(id, iconPath, tooltip, onLeftClick, menuContent)
            return
        }

        // Rebuild menu if content provided
        val newMenuItems = if (menuContent != null) {
            val builder = MacTrayMenuBuilderImpl(
                iconPath,
                tooltip,
                onLeftClick,
                trayManager = manager
            ).apply {
                menuContent()
            }
            trayMenuBuilders.remove(id)?.dispose()
            trayMenuBuilders[id] = builder
            builder.build()
        } else null

        manager.update(iconPath, tooltip, onLeftClick, newMenuItems)
    }

    @Synchronized
    fun setAppearanceIcons(id: String, lightIconPath: String, darkIconPath: String) {
        trayManagers[id]?.setAppearanceIcons(lightIconPath, darkIconPath)
    }

    @Synchronized
    fun dispose(id: String) {
        trayMenuBuilders.remove(id)?.dispose()
        trayManagers.remove(id)?.stopTray()
    }

    // Backward-compatible API for existing callers (single default tray)
    fun initialize(iconPath: String, tooltip: String, onLeftClick: (() -> Unit)? = null, menuContent: (TrayMenuBuilder.() -> Unit)? = null) =
        initialize(DEFAULT_ID, iconPath, tooltip, onLeftClick, menuContent)

    fun update(iconPath: String, tooltip: String, onLeftClick: (() -> Unit)? = null, menuContent: (TrayMenuBuilder.() -> Unit)? = null) =
        update(DEFAULT_ID, iconPath, tooltip, onLeftClick, menuContent)

    fun dispose() = dispose(DEFAULT_ID)
}