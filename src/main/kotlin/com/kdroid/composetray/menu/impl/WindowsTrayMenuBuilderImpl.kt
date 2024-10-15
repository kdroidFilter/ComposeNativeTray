package com.kdroid.composetray.menu.impl

import com.kdroid.composetray.lib.windows.WindowsTrayManager
import com.kdroid.composetray.menu.api.TrayMenuBuilder
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal class WindowsTrayMenuBuilderImpl(val iconPath : String, val tooltip : String = "") : TrayMenuBuilder {
    private val menuItems = mutableListOf<WindowsTrayManager.MenuItem>()
    private val lock = ReentrantLock()

    // Maintain persistent references to prevent GC
    private val persistentMenuItems = mutableListOf<WindowsTrayManager.MenuItem>()

    override fun Item(label: String, isEnabled: Boolean, onClick: () -> Unit) {
        lock.withLock {
            val menuItem = WindowsTrayManager.MenuItem(
                text = label,
                isEnabled = isEnabled,
                onClick = onClick
            )
            menuItems.add(menuItem)
            persistentMenuItems.add(menuItem) // Store reference to prevent GC
        }
    }

    override fun CheckableItem(label: String, isEnabled: Boolean, onToggle: (Boolean) -> Unit) {
        var isChecked = false // Initialise l'état checked

        lock.withLock {
            val menuItem = WindowsTrayManager.MenuItem(
                text = label,
                isEnabled = isEnabled,
                isCheckable = true,
                isChecked = isChecked,
                onClick = {
                    lock.withLock {
                        // Inverts the checked state
                        isChecked = !isChecked
                        onToggle(isChecked)

                        // Updates the item in the menuItems list
                        val itemIndex = menuItems.indexOfFirst { it.text == label }
                        if (itemIndex != -1) {
                            menuItems[itemIndex] = menuItems[itemIndex].copy(isChecked = isChecked)
                        }
                    }
                }
            )
            menuItems.add(menuItem)
            persistentMenuItems.add(menuItem) // Store reference to prevent GC
        }
    }

    override fun SubMenu(label: String, isEnabled: Boolean, submenuContent: TrayMenuBuilder.() -> Unit) {
        val subMenuItems = mutableListOf<WindowsTrayManager.MenuItem>()
        val subMenuImpl = WindowsTrayMenuBuilderImpl(iconPath, tooltip).apply(submenuContent)
        subMenuItems.addAll(subMenuImpl.menuItems)

        lock.withLock {
            val subMenu = WindowsTrayManager.MenuItem(
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
            val divider = WindowsTrayManager.MenuItem(text = "-")
            menuItems.add(divider)
            persistentMenuItems.add(divider) // Store reference to prevent GC
        }
    }

    override fun dispose() {
        lock.withLock {
            WindowsTrayManager(iconPath = iconPath, tooltip = tooltip).stopTray()
            persistentMenuItems.clear() // Clear references when disposing
        }
    }

    fun build(): List<WindowsTrayManager.MenuItem> = lock.withLock { menuItems.toList() }
}