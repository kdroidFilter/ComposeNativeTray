package com.kdroid.composetray.menu.impl

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import com.kdroid.composetray.menu.api.TrayMenuBuilder
import com.kdroid.composetray.lib.linux.LinuxTrayManager
import com.kdroid.composetray.lib.linux.LinuxTrayController
import com.kdroid.composetray.utils.ComposableIconUtils
import com.kdroid.composetray.utils.IconRenderProperties
import com.kdroid.composetray.utils.isMenuBarInDarkMode
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal class LinuxTrayMenuBuilderImpl(
    private val iconPath: String,
    private val tooltip: String = "",
    private val onLeftClick: (() -> Unit)?,
    private val trayManager: LinuxTrayController? = null
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
        lock.withLock {
            // Render the composable icon to a PNG file
            val iconPath = ComposableIconUtils.renderComposableToPngFile(iconRenderProperties, iconContent)

            val menuItem = LinuxTrayManager.MenuItem(
                text = label,
                isEnabled = isEnabled,
                iconPath = iconPath,
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
        lock.withLock {
            // Render the composable icon to a PNG file
            val iconPath = ComposableIconUtils.renderComposableToPngFile(iconRenderProperties, iconContent)

            val initialChecked = checked

            val menuItem = LinuxTrayManager.MenuItem(
                text = label,
                isEnabled = isEnabled,
                isCheckable = true,
                isChecked = initialChecked,
                iconPath = iconPath,
                onClick = {
                    lock.withLock {
                        val currentMenuItem = menuItems.find { it.text == label }
                        val currentChecked = currentMenuItem?.isChecked ?: initialChecked
                        val newChecked = !currentChecked

                        onCheckedChange(newChecked)
                        trayManager?.updateMenuItemCheckedState(label, newChecked)
                    }
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
        val subMenuItems = mutableListOf<LinuxTrayManager.MenuItem>()
        if (submenuContent != null) {
            val subMenuImpl = LinuxTrayMenuBuilderImpl(
                iconPath,
                tooltip,
                onLeftClick,
                trayManager
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
        val subMenuItems = mutableListOf<LinuxTrayManager.MenuItem>()
        if (submenuContent != null) {
            val subMenuImpl = LinuxTrayMenuBuilderImpl(
                iconPath,
                tooltip,
                onLeftClick,
                trayManager
            ).apply(submenuContent)
            subMenuItems.addAll(subMenuImpl.menuItems)
        }

        lock.withLock {
            // Render the composable icon to a PNG file
            val iconPath = ComposableIconUtils.renderComposableToPngFile(iconRenderProperties, iconContent)

            val subMenu = LinuxTrayManager.MenuItem(
                text = label,
                isEnabled = isEnabled,
                iconPath = iconPath,  // Maintenant supporté !
                subMenuItems = subMenuItems
            )
            menuItems.add(subMenu)
            persistentMenuItems.add(subMenu)
        }
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