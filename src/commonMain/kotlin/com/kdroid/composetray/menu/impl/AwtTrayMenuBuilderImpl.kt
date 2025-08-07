package com.kdroid.composetray.menu.impl

import com.kdroid.composetray.menu.api.TrayMenuBuilder
import java.awt.MenuItem
import java.awt.PopupMenu
import java.awt.SystemTray
import java.awt.TrayIcon

internal class AwtTrayMenuBuilderImpl(private val popupMenu: PopupMenu, private val trayIcon: TrayIcon) : TrayMenuBuilder {
    override fun Item(label: String, isEnabled: Boolean, onClick: () -> Unit) {
        val menuItem = MenuItem(label)
        menuItem.isEnabled = isEnabled
        menuItem.addActionListener {
            onClick()
        }
        popupMenu.add(menuItem)
    }

    override fun CheckableItem(
        label: String,
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit,
        isEnabled: Boolean
    ) {
        var currentChecked = checked
        val checkableMenuItem = MenuItem(getCheckableLabel(label, currentChecked))
        checkableMenuItem.isEnabled = isEnabled

        checkableMenuItem.addActionListener {
            val newChecked = !currentChecked
            currentChecked = newChecked
            checkableMenuItem.label = getCheckableLabel(label, newChecked)
            onCheckedChange(newChecked)
        }

        popupMenu.add(checkableMenuItem)
    }

    override fun SubMenu(label: String, isEnabled: Boolean, submenuContent: (TrayMenuBuilder.() -> Unit)?) {
        val subMenu = PopupMenu(label)
        subMenu.isEnabled = isEnabled
        submenuContent?.let { AwtTrayMenuBuilderImpl(subMenu, trayIcon).apply(it) }
        popupMenu.add(subMenu)
    }

    override fun Divider() {
        popupMenu.addSeparator()
    }

    override fun dispose() {
        SystemTray.getSystemTray().remove(trayIcon)
    }

    private fun getCheckableLabel(label: String, isChecked: Boolean): String {
        return if (isChecked) "✔ $label" else label
    }
}