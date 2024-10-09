package com.kdroid.menu

import java.awt.MenuItem
import java.awt.PopupMenu

class AwtTrayMenuImpl(private val popupMenu: PopupMenu) : TrayMenu {
    override fun Item(label: String, onClick: () -> Unit) {
        val menuItem = MenuItem(label)
        menuItem.addActionListener {
            onClick()
        }
        popupMenu.add(menuItem)
    }

    override fun CheckableItem(label: String, onToggle: (Boolean) -> Unit) {
        var isChecked = false
        val checkableMenuItem = MenuItem(getCheckableLabel(label, isChecked))

        checkableMenuItem.addActionListener {
            isChecked = !isChecked
            checkableMenuItem.label = getCheckableLabel(label, isChecked)
            onToggle(isChecked)
        }

        popupMenu.add(checkableMenuItem)
    }

    override fun SubMenu(label: String, submenuContent: TrayMenu.() -> Unit) {
        val subMenu = PopupMenu(label)
        AwtTrayMenuImpl(subMenu).apply(submenuContent)
        popupMenu.add(subMenu)
    }

    override fun Divider() {
        popupMenu.addSeparator()
    }
    private fun getCheckableLabel(label: String, isChecked: Boolean): String {
        return if (isChecked) "[✔] $label" else "[ ] $label"
    }

}