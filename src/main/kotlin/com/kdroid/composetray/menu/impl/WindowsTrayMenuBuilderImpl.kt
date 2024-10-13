package com.kdroid.composetray.menu.impl

import com.kdroid.composetray.lib.windows.WindowsTrayManager
import com.kdroid.composetray.menu.api.TrayMenuBuilder

internal class WindowsTrayMenuBuilderImpl(val iconPath : String, val tooltip : String = "") : TrayMenuBuilder {
    private val menuItems = mutableListOf<WindowsTrayManager.MenuItem>()

    override fun Item(label: String, isEnabled: Boolean, onClick: () -> Unit) {
        menuItems.add(
            WindowsTrayManager.MenuItem(
                text = label,
                isEnabled = isEnabled,
                onClick = onClick
            )
        )
    }

    override fun CheckableItem(label: String, isEnabled: Boolean, onToggle: (Boolean) -> Unit) {
        var isChecked = false // Initialise l'état checked

        menuItems.add(
            WindowsTrayManager.MenuItem(
                text = label,
                isEnabled = isEnabled,
                isCheckable = true,
                isChecked = isChecked,
                onClick = {
                    // Inverse l'état checked
                    isChecked = !isChecked
                    onToggle(isChecked)

                    // Met à jour l'élément dans la liste des menuItems
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

        menuItems.add(
            WindowsTrayManager.MenuItem(
                text = label,
                isEnabled = isEnabled,
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