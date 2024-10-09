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

    override fun SubMenu(label: String, submenuContent: TrayMenu.() -> Unit) {
        val subMenu = PopupMenu(label)
        AwtTrayMenuImpl(subMenu).apply(submenuContent)
        popupMenu.add(subMenu)
    }

    override fun Divider() {
        popupMenu.addSeparator()
    }
}