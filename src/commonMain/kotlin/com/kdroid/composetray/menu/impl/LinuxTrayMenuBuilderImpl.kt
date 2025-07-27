package com.kdroid.composetray.menu.impl

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import com.kdroid.composetray.menu.api.TrayMenuBuilder
import com.kdroid.composetray.lib.linux.LinuxTrayManager
import com.kdroid.composetray.utils.IconRenderProperties
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
    
    override fun Item(
        label: String,
        iconContent: @Composable () -> Unit,
        iconRenderProperties: IconRenderProperties,
        isEnabled: Boolean,
        onClick: () -> Unit
    ) {
        // Minimal implementation to make it compile
        // Actual icon integration will be handled by the issue creator
        Item(label, isEnabled, onClick)
    }
    
    override fun Item(
        label: String,
        icon: ImageVector,
        iconTint: Color?,
        iconRenderProperties: IconRenderProperties,
        isEnabled: Boolean,
        onClick: () -> Unit
    ) {
        // Minimal implementation to make it compile
        // Actual icon integration will be handled by the issue creator
        Item(label, isEnabled, onClick)
    }
    
    override fun Item(
        label: String,
        icon: Painter,
        iconRenderProperties: IconRenderProperties,
        isEnabled: Boolean,
        onClick: () -> Unit
    ) {
        // Minimal implementation to make it compile
        // Actual icon integration will be handled by the issue creator
        Item(label, isEnabled, onClick)
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
    
    override fun CheckableItem(
        label: String,
        iconContent: @Composable () -> Unit,
        iconRenderProperties: IconRenderProperties,
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit,
        isEnabled: Boolean
    ) {
        // Minimal implementation to make it compile
        // Actual icon integration will be handled by the issue creator
        CheckableItem(label, checked, onCheckedChange, isEnabled)
    }
    
    override fun CheckableItem(
        label: String,
        icon: ImageVector,
        iconTint: Color?,
        iconRenderProperties: IconRenderProperties,
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit,
        isEnabled: Boolean
    ) {
        // Minimal implementation to make it compile
        // Actual icon integration will be handled by the issue creator
        CheckableItem(label, checked, onCheckedChange, isEnabled)
    }
    
    override fun CheckableItem(
        label: String,
        icon: Painter,
        iconRenderProperties: IconRenderProperties,
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit,
        isEnabled: Boolean
    ) {
        // Minimal implementation to make it compile
        // Actual icon integration will be handled by the issue creator
        CheckableItem(label, checked, onCheckedChange, isEnabled)
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
    
    override fun SubMenu(
        label: String,
        iconContent: @Composable () -> Unit,
        iconRenderProperties: IconRenderProperties,
        isEnabled: Boolean,
        submenuContent: (TrayMenuBuilder.() -> Unit)?
    ) {
        // Minimal implementation to make it compile
        // Actual icon integration will be handled by the issue creator
        SubMenu(label, isEnabled, submenuContent)
    }
    
    override fun SubMenu(
        label: String,
        icon: ImageVector,
        iconTint: Color?,
        iconRenderProperties: IconRenderProperties,
        isEnabled: Boolean,
        submenuContent: (TrayMenuBuilder.() -> Unit)?
    ) {
        // Minimal implementation to make it compile
        // Actual icon integration will be handled by the issue creator
        SubMenu(label, isEnabled, submenuContent)
    }
    
    override fun SubMenu(
        label: String,
        icon: Painter,
        iconRenderProperties: IconRenderProperties,
        isEnabled: Boolean,
        submenuContent: (TrayMenuBuilder.() -> Unit)?
    ) {
        // Minimal implementation to make it compile
        // Actual icon integration will be handled by the issue creator
        SubMenu(label, isEnabled, submenuContent)
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