package com.kdroid.composetray.menu.impl

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import com.kdroid.composetray.lib.windows.WindowsTrayManager
import com.kdroid.composetray.menu.api.TrayMenuBuilder
import com.kdroid.composetray.utils.ComposableIconUtils
import com.kdroid.composetray.utils.IconRenderProperties
import com.kdroid.composetray.utils.isMenuBarInDarkMode
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
                iconPath = null,  // No icon for basic item
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
        lock.withLock {
            // Render the composable icon to a PNG file (for future Windows icon support)
            val iconPath = ComposableIconUtils.renderComposableToIcoFile(iconRenderProperties, iconContent)

            val menuItem = WindowsTrayManager.MenuItem(
                text = label,
                iconPath = iconPath,
                isEnabled = isEnabled,
                onClick = onClick
            )
            menuItems.add(menuItem)
            persistentMenuItems.add(menuItem)
        }
    }

    override fun Item(
        label: String,
        icon: ImageVector,
        iconTint: Color?,
        iconRenderProperties: IconRenderProperties,
        isEnabled: Boolean,
        onClick: () -> Unit
    ) {
        // Create composable content for the icon
        val iconContent: @Composable () -> Unit = {
            val isDark = isMenuBarInDarkMode()

            Image(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                colorFilter = iconTint?.let { ColorFilter.tint(it) }
                    ?: if (isDark) ColorFilter.tint(Color.White)
                    else ColorFilter.tint(Color.Black)
            )
        }

        // Delegate to the composable version
        Item(label, iconContent, iconRenderProperties, isEnabled, onClick)
    }

    override fun Item(
        label: String,
        icon: Painter,
        iconRenderProperties: IconRenderProperties,
        isEnabled: Boolean,
        onClick: () -> Unit
    ) {
        // Create composable content for the painter
        val iconContent: @Composable () -> Unit = {
            Image(
                painter = icon,
                contentDescription = null,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Delegate to the composable version
        Item(label, iconContent, iconRenderProperties, isEnabled, onClick)
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
                iconPath = null,
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
        lock.withLock {
            // Render the composable icon to a PNG file (for future Windows icon support)
            val iconPath = ComposableIconUtils.renderComposableToIcoFile(iconRenderProperties, iconContent)

            val menuItem = WindowsTrayManager.MenuItem(
                text = label,
                iconPath = iconPath,
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
            persistentMenuItems.add(menuItem)
        }
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
        // Create composable content for the icon
        val iconContent: @Composable () -> Unit = {
            val isDark = isMenuBarInDarkMode()

            Image(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                colorFilter = iconTint?.let { ColorFilter.tint(it) }
                    ?: if (isDark) ColorFilter.tint(Color.White)
                    else ColorFilter.tint(Color.Black)
            )
        }

        // Delegate to the composable version
        CheckableItem(label, iconContent, iconRenderProperties, checked, onCheckedChange, isEnabled)
    }

    override fun CheckableItem(
        label: String,
        icon: Painter,
        iconRenderProperties: IconRenderProperties,
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit,
        isEnabled: Boolean
    ) {
        // Create composable content for the painter
        val iconContent: @Composable () -> Unit = {
            Image(
                painter = icon,
                contentDescription = null,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Delegate to the composable version
        CheckableItem(label, iconContent, iconRenderProperties, checked, onCheckedChange, isEnabled)
    }

    override fun SubMenu(label: String, isEnabled: Boolean, submenuContent: (TrayMenuBuilder.() -> Unit)?) {
        createSubMenu(label, null, isEnabled, submenuContent)
    }

    override fun SubMenu(
        label: String,
        iconContent: @Composable () -> Unit,
        iconRenderProperties: IconRenderProperties,
        isEnabled: Boolean,
        submenuContent: (TrayMenuBuilder.() -> Unit)?
    ) {
        // Render the composable icon to a PNG file (for future Windows icon support)
        val iconPath = ComposableIconUtils.renderComposableToIcoFile(iconRenderProperties, iconContent)
        createSubMenu(label, iconPath, isEnabled, submenuContent)
    }

    override fun SubMenu(
        label: String,
        icon: ImageVector,
        iconTint: Color?,
        iconRenderProperties: IconRenderProperties,
        isEnabled: Boolean,
        submenuContent: (TrayMenuBuilder.() -> Unit)?
    ) {
        // Create composable content for the icon
        val iconContent: @Composable () -> Unit = {
            val isDark = isMenuBarInDarkMode()

            Image(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                colorFilter = iconTint?.let { ColorFilter.tint(it) }
                    ?: if (isDark) ColorFilter.tint(Color.White)
                    else ColorFilter.tint(Color.Black)
            )
        }

        // Delegate to the composable version
        SubMenu(label, iconContent, iconRenderProperties, isEnabled, submenuContent)
    }

    override fun SubMenu(
        label: String,
        icon: Painter,
        iconRenderProperties: IconRenderProperties,
        isEnabled: Boolean,
        submenuContent: (TrayMenuBuilder.() -> Unit)?
    ) {
        // Create composable content for the painter
        val iconContent: @Composable () -> Unit = {
            Image(
                painter = icon,
                contentDescription = null,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Delegate to the composable version
        SubMenu(label, iconContent, iconRenderProperties, isEnabled, submenuContent)
    }

    // Private helper method to create submenu
    private fun createSubMenu(
        label: String,
        iconPath: String?,
        isEnabled: Boolean,
        submenuContent: (TrayMenuBuilder.() -> Unit)?
    ) {
        val subMenuItems = mutableListOf<WindowsTrayManager.MenuItem>()
        if (submenuContent != null) {
            val subMenuImpl = WindowsTrayMenuBuilderImpl(this.iconPath, tooltip, onLeftClick = onLeftClick).apply(submenuContent)
            subMenuItems.addAll(subMenuImpl.menuItems)
        }
        lock.withLock {
            val subMenu = WindowsTrayManager.MenuItem(
                text = label,
                iconPath = iconPath,
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
            WindowsTrayManager(iconPath = iconPath, tooltip = tooltip, onLeftClick = onLeftClick).stopTray()
            persistentMenuItems.clear() // Clear references when disposing
        }
    }

    fun build(): List<WindowsTrayManager.MenuItem> = lock.withLock { menuItems.toList() }
}