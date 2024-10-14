package com.kdroid.composetray.menu.impl

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kdroid.composetray.lib.windows.WindowsTrayManager
import com.kdroid.composetray.menu.api.TrayMenuBuilder

internal class WindowsTrayMenuBuilderImpl(private val iconPath: String, val tooltip: String = "") : TrayMenuBuilder {
    private val menuItems = mutableListOf<WindowsTrayManager.MenuItem>()

    override fun Item(label: String, isEnabled: Boolean, onClick: () -> Unit) {
        val isEnabledState = mutableStateOf(isEnabled)

        menuItems.add(
            WindowsTrayManager.MenuItem(
                text = label,
                isEnabled = isEnabledState.value,
                onClick = {
                    onClick()
                    // Recomposition triggered here
                    isEnabledState.value = isEnabledState.value // Mise à jour pour trigger la recomposition
                }
            )
        )
    }

    override fun CheckableItem(label: String, isEnabled: Boolean, onToggle: (Boolean) -> Unit) {
        var isChecked by mutableStateOf(false) // Initialise l'état checked comme mutable

        menuItems.add(
            WindowsTrayManager.MenuItem(
                text = label,
                isEnabled = isEnabled,
                isCheckable = true,
                isChecked = isChecked,
                onClick = {
                    // Inverse l'état checked et notifie la modification
                    isChecked = !isChecked
                    onToggle(isChecked)

                    // Mise à jour de l'item dans la liste menuItems
                    val itemIndex = menuItems.indexOfFirst { it.text == label }
                    if (itemIndex != -1) {
                        menuItems[itemIndex] = menuItems[itemIndex].copy(isChecked = isChecked)
                    }
                }
            )
        )
    }

    override fun SubMenu(label: String, isEnabled: Boolean, submenuContent: TrayMenuBuilder.() -> Unit) {
        val subMenuItems = mutableListOf<WindowsTrayManager.MenuItem>()
        val subMenuImpl = WindowsTrayMenuBuilderImpl(iconPath, tooltip).apply(submenuContent)
        subMenuItems.addAll(subMenuImpl.menuItems)

        val isEnabledState = mutableStateOf(isEnabled)

        menuItems.add(
            WindowsTrayManager.MenuItem(
                text = label,
                isEnabled = isEnabledState.value,
                subMenuItems = subMenuItems
            )
        )
    }

    override fun Divider() {
        menuItems.add(WindowsTrayManager.MenuItem(text = "-"))
    }

    override fun dispose() {
        WindowsTrayManager(iconPath = iconPath, tooltip = tooltip).stopTray()
    }

    fun build(): List<WindowsTrayManager.MenuItem> = menuItems
}
