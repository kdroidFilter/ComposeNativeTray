package com.kdroid.composetray.menu

import com.kdroid.composetray.lib.windows.WindowsTrayManager

class WindowsTrayMenuImpl(val iconPath : String, val tooltip : String = "") : TrayMenu {
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
        menuItems.add(
            WindowsTrayManager.MenuItem(
                text = label,
                isEnabled = isEnabled,
                isCheckable = true,
                onClick = {
                    val currentState = menuItems.find { it.text == label }?.isChecked ?: false
                    onToggle(!currentState)
                }
            )
        )
    }

    override fun SubMenu(label: String, isEnabled: Boolean, submenuContent: TrayMenu.() -> Unit) {
        val subMenuItems = mutableListOf<WindowsTrayManager.MenuItem>()
        val subMenuImpl = WindowsTrayMenuImpl(iconPath, tooltip).apply(submenuContent)
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