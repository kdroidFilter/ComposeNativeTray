package com.kdroid.composetray.menu.impl

import com.kdroid.composetray.lib.mac.MacTrayManager
import com.kdroid.composetray.menu.api.TrayMenuBuilder
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal class MacTrayMenuBuilderImpl(
    private val iconPath: String,
    private val tooltip: String = "",
    private val onLeftClick: (() -> Unit)?,
    private val trayManager: MacTrayManager? = null
) : TrayMenuBuilder {
    private val menuItems = mutableListOf<MacTrayManager.MenuItem>()
    private val lock = ReentrantLock()

    // Maintain persistent references to prevent GC
    private val persistentMenuItems = mutableListOf<MacTrayManager.MenuItem>()

    override fun Item(label: String, isEnabled: Boolean, onClick: () -> Unit) {
        lock.withLock {
            val menuItem = MacTrayManager.MenuItem(
                text = label,
                isEnabled = isEnabled,
                onClick = onClick
            )
            menuItems.add(menuItem)
            persistentMenuItems.add(menuItem) // Store reference to prevent GC
        }
    }

    override fun CheckableItem(label: String, checked: Boolean, isEnabled: Boolean, onToggle: (Boolean) -> Unit) {
        var isChecked = checked // Initialize checked state

        lock.withLock {
            val menuItem = MacTrayManager.MenuItem(
                text = label,
                isEnabled = isEnabled,
                isCheckable = true,
                isChecked = isChecked,
                onClick = {
                    lock.withLock {
                        // Inverts the checked state
                        isChecked = !isChecked
                        onToggle(isChecked)

                        // Update the menu item in the tray manager
                        trayManager?.updateMenuItemCheckedState(label, isChecked)
                    }
                }
            )
            menuItems.add(menuItem)
            persistentMenuItems.add(menuItem) // Store reference to prevent GC
        }
    }

    override fun SubMenu(label: String, isEnabled: Boolean, submenuContent: (TrayMenuBuilder.() -> Unit)?) {
        val subMenuItems = mutableListOf<MacTrayManager.MenuItem>()
        if (submenuContent != null) {
            val subMenuImpl = MacTrayMenuBuilderImpl(
                iconPath,
                tooltip,
                onLeftClick = onLeftClick,
                trayManager = trayManager
            ).apply(submenuContent)
            subMenuItems.addAll(subMenuImpl.menuItems)
        }
        lock.withLock {
            val subMenu = MacTrayManager.MenuItem(
                text = label,
                isEnabled = isEnabled,
                subMenuItems = subMenuItems
            )
            menuItems.add(subMenu)
            persistentMenuItems.add(subMenu) // Store reference to prevent GC
        }
    }

    override fun Divider() {
        lock.withLock {
            val divider = MacTrayManager.MenuItem(text = "-")
            menuItems.add(divider)
            persistentMenuItems.add(divider) // Store reference to prevent GC
        }
    }

    override fun dispose() {
        lock.withLock {
            // Just clear references when disposing
            // The actual MacTrayManager instance is managed by MacTrayInitializer
            persistentMenuItems.clear()
        }
    }

    fun build(): List<MacTrayManager.MenuItem> = lock.withLock { menuItems.toList() }
}