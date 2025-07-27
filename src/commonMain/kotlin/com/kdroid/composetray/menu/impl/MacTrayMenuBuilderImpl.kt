package com.kdroid.composetray.menu.impl

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import com.kdroid.composetray.lib.mac.MacTrayManager
import com.kdroid.composetray.menu.api.TrayMenuBuilder
import com.kdroid.composetray.utils.ComposableIconUtils
import com.kdroid.composetray.utils.IconRenderProperties
import com.kdroid.composetray.utils.isMenuBarInDarkMode
import io.github.kdroidfilter.platformtools.darkmodedetector.isSystemInDarkMode
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

    // Item without icon (existing method)
    override fun Item(label: String, isEnabled: Boolean, onClick: () -> Unit) {
        lock.withLock {
            val menuItem = MacTrayManager.MenuItem(
                text = label,
                icon = null,
                isEnabled = isEnabled,
                onClick = onClick
            )
            menuItems.add(menuItem)
            persistentMenuItems.add(menuItem)
        }
    }

    // Item with Composable icon
    override fun Item(
        label: String,
        iconContent: @Composable () -> Unit,
        iconRenderProperties: IconRenderProperties,
        isEnabled: Boolean,
        onClick: () -> Unit
    ) {
        lock.withLock {
            // Render the composable icon to a PNG file
            val iconPath = ComposableIconUtils.renderComposableToPngFile(iconRenderProperties, iconContent)

            val menuItem = MacTrayManager.MenuItem(
                text = label,
                icon = iconPath,
                isEnabled = isEnabled,
                onClick = onClick
            )
            menuItems.add(menuItem)
            persistentMenuItems.add(menuItem)
        }
    }

    // Item with ImageVector icon
    override fun Item(
        label: String,
        icon: ImageVector,
        iconTint: Color?,
        iconRenderProperties: IconRenderProperties,
        isEnabled: Boolean,
        onClick: () -> Unit
    ) {


        // Create a composable that renders the ImageVector with appropriate tint
        val iconContent: @Composable () -> Unit = {
            // Get the current menu bar theme
            val isDark = isSystemInDarkMode()
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

    // Item with Painter icon
    override fun Item(
        label: String,
        icon: Painter,
        iconRenderProperties: IconRenderProperties,
        isEnabled: Boolean,
        onClick: () -> Unit
    ) {
        // Create a composable that renders the Painter
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

    // CheckableItem without icon (existing method)
    override fun CheckableItem(
        label: String,
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit,
        isEnabled: Boolean
    ) {
        lock.withLock {
            val menuItem = MacTrayManager.MenuItem(
                text = label,
                icon = null,
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
            persistentMenuItems.add(menuItem)
        }
    }

    // CheckableItem with Composable icon
    override fun CheckableItem(
        label: String,
        iconContent: @Composable () -> Unit,
        iconRenderProperties: IconRenderProperties,
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit,
        isEnabled: Boolean
    ) {
        lock.withLock {
            // Render the composable icon to a PNG file
            val iconPath = ComposableIconUtils.renderComposableToPngFile(iconRenderProperties, iconContent)

            val menuItem = MacTrayManager.MenuItem(
                text = label,
                icon = iconPath,
                isEnabled = isEnabled,
                isCheckable = true,
                isChecked = checked,
                onClick = {
                    lock.withLock {
                        val newChecked = !checked
                        onCheckedChange(newChecked)
                    }
                }
            )
            menuItems.add(menuItem)
            persistentMenuItems.add(menuItem)
        }
    }

    // CheckableItem with ImageVector icon
    override fun CheckableItem(
        label: String,
        icon: ImageVector,
        iconTint: Color?,
        iconRenderProperties: IconRenderProperties,
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit,
        isEnabled: Boolean
    ) {
        // Create a composable that renders the ImageVector with appropriate tint
        val iconContent: @Composable () -> Unit = {
            // Get the current menu bar theme
            val isDark = isSystemInDarkMode()
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

    // CheckableItem with Painter icon
    override fun CheckableItem(
        label: String,
        icon: Painter,
        iconRenderProperties: IconRenderProperties,
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit,
        isEnabled: Boolean
    ) {
        // Create a composable that renders the Painter
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

    // SubMenu without icon (existing method)
    override fun SubMenu(label: String, isEnabled: Boolean, submenuContent: (TrayMenuBuilder.() -> Unit)?) {
        createSubMenu(label, null, isEnabled, submenuContent)
    }

    // SubMenu with Composable icon
    override fun SubMenu(
        label: String,
        iconContent: @Composable () -> Unit,
        iconRenderProperties: IconRenderProperties,
        isEnabled: Boolean,
        submenuContent: (TrayMenuBuilder.() -> Unit)?
    ) {
        // Render the composable icon to a PNG file
        val iconPath = ComposableIconUtils.renderComposableToPngFile(iconRenderProperties, iconContent)
        createSubMenu(label, iconPath, isEnabled, submenuContent)
    }

    // SubMenu with ImageVector icon
    override fun SubMenu(
        label: String,
        icon: ImageVector,
        iconTint: Color?,
        iconRenderProperties: IconRenderProperties,
        isEnabled: Boolean,
        submenuContent: (TrayMenuBuilder.() -> Unit)?
    ) {
        // Create a composable that renders the ImageVector with appropriate tint
        val iconContent: @Composable () -> Unit = {
            // Get the current menu bar theme
            val isDark = isSystemInDarkMode()
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

    // SubMenu with Painter icon
    override fun SubMenu(
        label: String,
        icon: Painter,
        iconRenderProperties: IconRenderProperties,
        isEnabled: Boolean,
        submenuContent: (TrayMenuBuilder.() -> Unit)?
    ) {
        // Create a composable that renders the Painter
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
        val subMenuItems = mutableListOf<MacTrayManager.MenuItem>()
        if (submenuContent != null) {
            val subMenuImpl = MacTrayMenuBuilderImpl(
                iconPath = this.iconPath,
                tooltip = tooltip,
                onLeftClick = onLeftClick,
                trayManager = trayManager
            ).apply(submenuContent)
            subMenuItems.addAll(subMenuImpl.menuItems)
        }
        lock.withLock {
            val subMenu = MacTrayManager.MenuItem(
                text = label,
                icon = iconPath,
                isEnabled = isEnabled,
                subMenuItems = subMenuItems
            )
            menuItems.add(subMenu)
            persistentMenuItems.add(subMenu)
        }
    }

    override fun Divider() {
        lock.withLock {
            val divider = MacTrayManager.MenuItem(text = "-")
            menuItems.add(divider)
            persistentMenuItems.add(divider)
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