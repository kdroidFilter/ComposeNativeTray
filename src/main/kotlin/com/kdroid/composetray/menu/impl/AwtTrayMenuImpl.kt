package com.kdroid.composetray.menu.impl

import com.kdroid.composetray.menu.TrayMenu
import java.awt.MenuItem
import java.awt.PopupMenu
import java.awt.SystemTray
import java.awt.TrayIcon

internal class AwtTrayMenuImpl(private val popupMenu: PopupMenu, private val trayIcon: TrayIcon) : TrayMenu {
    override fun Item(label: String, isEnabled: Boolean, onClick: () -> Unit) {
        val menuItem = MenuItem(label)
        menuItem.isEnabled = isEnabled
        menuItem.addActionListener {
            onClick()
        }
        popupMenu.add(menuItem)
    }

    override fun CheckableItem(label: String, isEnabled: Boolean, onToggle: (Boolean) -> Unit) {
        var isChecked = false
        val checkableMenuItem = MenuItem(getCheckableLabel(label, isChecked))
        checkableMenuItem.isEnabled = isEnabled

        checkableMenuItem.addActionListener {
            isChecked = !isChecked
            checkableMenuItem.label = getCheckableLabel(label, isChecked)
            onToggle(isChecked)
        }

        popupMenu.add(checkableMenuItem)
    }

    override fun SubMenu(label: String, isEnabled: Boolean, submenuContent: TrayMenu.() -> Unit) {
        val subMenu = PopupMenu(label)
        subMenu.isEnabled = isEnabled
        AwtTrayMenuImpl(subMenu, trayIcon).apply(submenuContent)
        popupMenu.add(subMenu)
    }

    override fun Divider() {
        popupMenu.addSeparator()
    }

    override fun dispose() {
        SystemTray.getSystemTray().remove(trayIcon)
    }

    private fun getCheckableLabel(label: String, isChecked: Boolean): String {
        return if (isChecked) "[✔] $label" else "[ ] $label"
    }
}
