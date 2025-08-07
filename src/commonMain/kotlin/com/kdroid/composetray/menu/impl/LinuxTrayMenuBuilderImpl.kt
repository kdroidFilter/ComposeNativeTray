package com.kdroid.composetray.menu.impl

import com.kdroid.composetray.menu.api.TrayMenuBuilder
import com.kdroid.composetray.lib.linux.LinuxTrayManager
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal class LinuxTrayMenuBuilderImpl(
    private val iconPath: String,
    private val tooltip: String = "",
    private val onLeftClick: (() -> Unit)?,
    private val primaryActionLabel: String,
    private val trayManager: LinuxTrayManager? = null
) : TrayMenuBuilder {
    private val menuItems = mutableListOf<LinuxTrayManager.MenuItem>()
    private val lock = ReentrantLock()

    // Maintain persistent references to prevent GC
    private val persistentMenuItems = mutableListOf<LinuxTrayManager.MenuItem>()

    override fun Item(label: String, isEnabled: Boolean, onClick: () -> Unit) {
        lock.withLock {
            val menuItem = LinuxTrayManager.MenuItem(
                text = label,
                isEnabled = isEnabled,
                onClick = onClick
            )
            menuItems.add(menuItem)
            persistentMenuItems.add(menuItem) // Store reference
        }
    }

    override fun CheckableItem(
        label: String,
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit,
        isEnabled: Boolean
    ) {
        lock.withLock {
            // Create a mutable reference to the current checked state
            // This will be used in the onClick callback to get the current state
            // instead of capturing the initial state
            val initialChecked = checked
            
            val menuItem = LinuxTrayManager.MenuItem(
                text = label,
                isEnabled = isEnabled,
                isCheckable = true,
                isChecked = initialChecked,
                onClick = {
                    lock.withLock {
                        // Find the current menu item to get its current state
                        val currentMenuItem = menuItems.find { it.text == label }
                        // Toggle based on the current state, not the initial state
                        val currentChecked = currentMenuItem?.isChecked ?: initialChecked
                        val newChecked = !currentChecked
                        
                        // Call the onCheckedChange callback with the new state
                        onCheckedChange(newChecked)
                        
                        // Update the tray manager to reflect the new state
                        trayManager?.updateMenuItemCheckedState(label, newChecked)
                    }
                }
            )
            menuItems.add(menuItem)
            persistentMenuItems.add(menuItem) // Store reference
        }
    }

    override fun SubMenu(label: String, isEnabled: Boolean, submenuContent: (TrayMenuBuilder.() -> Unit)?) {
        val subMenuItems = mutableListOf<LinuxTrayManager.MenuItem>()
        if (submenuContent != null) {
            val subMenuImpl = LinuxTrayMenuBuilderImpl(
                iconPath,
                tooltip,
                onLeftClick,
                primaryActionLabel,
                trayManager = trayManager
            ).apply(submenuContent)
            subMenuItems.addAll(subMenuImpl.menuItems)
        }
        lock.withLock {
            val subMenu = LinuxTrayManager.MenuItem(
                text = label,
                isEnabled = isEnabled,
                subMenuItems = subMenuItems
            )
            menuItems.add(subMenu)
            persistentMenuItems.add(subMenu) // Store reference
        }
    }

    override fun Divider() {
        lock.withLock {
            val divider = LinuxTrayManager.MenuItem(text = "-")
            menuItems.add(divider)
            persistentMenuItems.add(divider) // Store reference
        }
    }

    override fun dispose() {
        lock.withLock {
            // Clear references when disposing
            persistentMenuItems.clear()
        }
    }

    fun build(): List<LinuxTrayManager.MenuItem> = lock.withLock { menuItems.toList() }
}