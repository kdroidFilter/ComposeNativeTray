package com.kdroid.composetray.tray.impl

import com.kdroid.composetray.lib.linux.LinuxTrayManager
import com.kdroid.composetray.lib.linux.SNIWrapper
import com.kdroid.composetray.menu.api.TrayMenuBuilder
import com.kdroid.composetray.menu.impl.LinuxTrayMenuBuilderImpl
import com.kdroid.composetray.utils.allowComposeNativeTrayLogging
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

object LinuxSNITrayInitializer {

    private const val DEFAULT_ID: String = "_default"

    private val trayMenuImpls: MutableMap<String, LinuxTrayMenuBuilderImpl> = mutableMapOf()
    private val linuxTrayManagers: MutableMap<String, LinuxTrayManager> = mutableMapOf()
    private val lock = ReentrantLock()

    init {
        val debugMode = allowComposeNativeTrayLogging
        try {
            SNIWrapper.INSTANCE.sni_set_debug_mode(if (debugMode) 1 else 0)
        } catch (e: Exception) {
        }
    }

    @Synchronized
    fun initialize(
        id: String,
        iconPath: String,
        tooltip: String,
        onLeftClick: (() -> Unit)? = null,
        menuContent: (TrayMenuBuilder.() -> Unit)? = null
    ) {
        lock.withLock {
            val existing = linuxTrayManagers[id]
            if (existing == null) {
                val manager = LinuxTrayManager(id, iconPath, tooltip, onLeftClick)
                linuxTrayManagers[id] = manager

                val menuImpl = if (menuContent != null) {
                    LinuxTrayMenuBuilderImpl(iconPath, tooltip, onLeftClick, trayManager = manager).apply {
                        menuContent()
                    }
                } else null
                menuImpl?.let { impl ->
                    trayMenuImpls[id]?.dispose()
                    trayMenuImpls[id] = impl
                    impl.build().forEach { item -> manager.addMenuItem(item) }
                }

                manager.startTray()
            } else {
                update(id, iconPath, tooltip, onLeftClick, menuContent)
            }
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
        lock.withLock {
            val manager = linuxTrayManagers[id]
            if (manager == null) {
                initialize(id, iconPath, tooltip, onLeftClick, menuContent)
                return
            }

            val newMenuItems = if (menuContent != null) {
                val newImpl = LinuxTrayMenuBuilderImpl(iconPath, tooltip, onLeftClick, trayManager = manager).apply {
                    menuContent()
                }
                trayMenuImpls[id]?.dispose()
                trayMenuImpls[id] = newImpl
                newImpl.build()
            } else null

            manager.update(iconPath, tooltip, onLeftClick, newMenuItems)
        }
    }

    @Synchronized
    fun dispose(id: String) {
        lock.withLock {
            linuxTrayManagers.remove(id)?.stopTray()
            trayMenuImpls.remove(id)?.dispose()
        }
    }

    // Backward-compatible single-tray API
    fun initialize(iconPath: String, tooltip: String, onLeftClick: (() -> Unit)? = null, menuContent: (TrayMenuBuilder.() -> Unit)? = null) =
        initialize(DEFAULT_ID, iconPath, tooltip, onLeftClick, menuContent)

    fun update(iconPath: String, tooltip: String, onLeftClick: (() -> Unit)? = null, menuContent: (TrayMenuBuilder.() -> Unit)? = null) =
        update(DEFAULT_ID, iconPath, tooltip, onLeftClick, menuContent)

    fun dispose() = dispose(DEFAULT_ID)
}