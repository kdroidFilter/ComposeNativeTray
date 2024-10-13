package com.kdroid.composetray.menu.impl

import com.kdroid.composetray.menu.api.TrayMenuBuilder
import javax.swing.JMenuItem
import javax.swing.JCheckBoxMenuItem
import javax.swing.JMenu
import javax.swing.JPopupMenu

internal class SwingTrayMenuBuilderImpl(private val popupMenu: JPopupMenu) : TrayMenuBuilder {
    override fun Item(label: String, isEnabled: Boolean, onClick: () -> Unit) {
        val menuItem = JMenuItem(label)
        menuItem.isEnabled = isEnabled
        menuItem.addActionListener {
            onClick()
        }
        popupMenu.add(menuItem)
    }

    override fun CheckableItem(label: String, isEnabled: Boolean, onToggle: (Boolean) -> Unit) {
        val checkableMenuItem = JCheckBoxMenuItem(label)
        checkableMenuItem.isEnabled = isEnabled

        checkableMenuItem.addActionListener {
            onToggle(checkableMenuItem.isSelected)
        }

        popupMenu.add(checkableMenuItem)
    }

    override fun SubMenu(label: String, isEnabled: Boolean, submenuContent: TrayMenuBuilder.() -> Unit) {
        val subMenu = JMenu(label)
        subMenu.isEnabled = isEnabled
        SwingTrayMenuBuilderImpl(JPopupMenu()).apply(submenuContent).apply {
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

    override fun dispose() {
        popupMenu.removeAll()
        popupMenu.isVisible = false
    }
}
