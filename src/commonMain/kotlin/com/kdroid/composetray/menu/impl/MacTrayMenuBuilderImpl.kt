package com.kdroid.composetray.menu.impl

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import com.kdroid.composetray.lib.mac.MacTrayManager
import com.kdroid.composetray.menu.api.TrayMenuBuilder
import com.kdroid.composetray.utils.IconRenderProperties
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
            val menuItem = MacTrayManager.MenuItem(
                text = label,
                isEnabled = isEnabled,
                isCheckable = true,
                isChecked = checked,
                onClick = {
                    lock.withLock {
                        // Toggle the checked state
                        val newChecked = !checked
                        onCheckedChange(newChecked)

                        // Note: The actual visual update of the check mark
                        // will happen when the menu is recreated after the state change
                    }
                }
            )
            menuItems.add(menuItem)
            persistentMenuItems.add(menuItem) // Store reference to prevent GC
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