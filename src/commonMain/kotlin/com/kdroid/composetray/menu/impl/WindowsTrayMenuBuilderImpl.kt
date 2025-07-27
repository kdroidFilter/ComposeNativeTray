package com.kdroid.composetray.menu.impl

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import com.kdroid.composetray.lib.windows.WindowsTrayManager
import com.kdroid.composetray.menu.api.TrayMenuBuilder
import com.kdroid.composetray.utils.IconRenderProperties
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal class WindowsTrayMenuBuilderImpl(
    private val iconPath: String,
    private val tooltip: String = "",
    private val onLeftClick: (() -> Unit)?
) : TrayMenuBuilder {
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
            val menuItem = WindowsTrayManager.MenuItem(
                text = label,
                isEnabled = isEnabled,
                isCheckable = true,
                isChecked = checked,
                onClick = {
                    // Toggle the checked state
                    val newChecked = !checked
                    onCheckedChange(newChecked)
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
        val subMenuItems = mutableListOf<WindowsTrayManager.MenuItem>()
        if (submenuContent != null) {
            val subMenuImpl = WindowsTrayMenuBuilderImpl(iconPath, tooltip, onLeftClick = onLeftClick).apply(submenuContent)
            subMenuItems.addAll(subMenuImpl.menuItems)
        }
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
            val divider = WindowsTrayManager.MenuItem(text = "-")
            menuItems.add(divider)
            persistentMenuItems.add(divider) // Store reference to prevent GC
        }
    }

    override fun dispose() {
        lock.withLock {
            WindowsTrayManager(iconPath = iconPath, tooltip = tooltip, onLeftClick = onLeftClick).stopTray()
            persistentMenuItems.clear() // Clear references when disposing
        }
    }

    fun build(): List<WindowsTrayManager.MenuItem> = lock.withLock { menuItems.toList() }
}