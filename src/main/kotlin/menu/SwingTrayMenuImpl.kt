package com.kdroid.menu

import javax.swing.JMenuItem
import javax.swing.JCheckBoxMenuItem
import javax.swing.JMenu
import javax.swing.JPopupMenu

class SwingTrayMenuImpl(private val popupMenu: JPopupMenu) : TrayMenu {
    override fun Item(label: String, onClick: () -> Unit) {
        val menuItem = JMenuItem(label)
        menuItem.addActionListener {
            onClick()
        }
        popupMenu.add(menuItem)
    }

    override fun CheckableItem(label: String, onToggle: (Boolean) -> Unit) {
        val checkableMenuItem = JCheckBoxMenuItem(label)

        checkableMenuItem.addActionListener {
            onToggle(checkableMenuItem.isSelected)
        }

        popupMenu.add(checkableMenuItem)
    }

    override fun SubMenu(label: String, submenuContent: TrayMenu.() -> Unit) {
        val subMenu = JMenu(label)
        SwingTrayMenuImpl(JPopupMenu()).apply(submenuContent).apply {
            val menuItems = popupMenu.components
            for (item in menuItems) {
                subMenu.add(item)
            }
        }
        popupMenu.add(subMenu)
    }

    override fun Divider() {
        popupMenu.addSeparator()
    }
}
