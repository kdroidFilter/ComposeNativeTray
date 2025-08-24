package com.kdroid.composetray.tray.impl

import com.kdroid.composetray.lib.windows.WindowsTrayManager
import com.kdroid.composetray.menu.api.TrayMenuBuilder
import com.kdroid.composetray.menu.impl.WindowsTrayMenuBuilderImpl

object WindowsTrayInitializer {

    private const val DEFAULT_ID: String = "_default"

    // Manage multiple tray managers by ID to allow multiple tray icons
    private val trayManagers: MutableMap<String, WindowsTrayManager> = mutableMapOf()

    @Synchronized
    fun initialize(
        id: String,
        iconPath: String,
        tooltip: String,
        onLeftClick: (() -> Unit)? = null,
        menuContent: (TrayMenuBuilder.() -> Unit)? = null
    ) {
        val menuItems = WindowsTrayMenuBuilderImpl(iconPath, tooltip, onLeftClick).apply {
            menuContent?.let { it() }
        }.build()

        val manager = trayManagers[id]
        if (manager == null) {
            val windowsTrayManager = WindowsTrayManager(id, iconPath, tooltip, onLeftClick)
            trayManagers[id] = windowsTrayManager
            windowsTrayManager.initialize(menuItems)
        } else {
            manager.update(iconPath, tooltip, onLeftClick, menuItems)
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
        // Same as initialize - it will handle both cases per ID
        initialize(id, iconPath, tooltip, onLeftClick, menuContent)
    }

    @Synchronized
    fun dispose(id: String) {
        trayManagers.remove(id)?.stopTray()
    }

    // Backward-compatible API for existing callers (single default tray)
    fun initialize(iconPath: String, tooltip: String, onLeftClick: (() -> Unit)? = null, menuContent: (TrayMenuBuilder.() -> Unit)? = null) =
        initialize(DEFAULT_ID, iconPath, tooltip, onLeftClick, menuContent)

    fun update(iconPath: String, tooltip: String, onLeftClick: (() -> Unit)? = null, menuContent: (TrayMenuBuilder.() -> Unit)? = null) =
        update(DEFAULT_ID, iconPath, tooltip, onLeftClick, menuContent)

    fun dispose() = dispose(DEFAULT_ID)
}