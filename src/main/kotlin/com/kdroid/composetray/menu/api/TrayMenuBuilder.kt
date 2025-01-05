package com.kdroid.composetray.menu.api

 /**
  * Interface for building tray menus in a platform-independent manner.
  * Implementations of this interface allow the creation of tray menus
  * with items, checkable items, submenus, and dividers, and provide a
  * mechanism for disposing resources when the menu is no longer needed.
  */
 interface TrayMenuBuilder {
    fun Item(label: String, isEnabled: Boolean = true, onClick: () -> Unit = {})
    fun CheckableItem(label: String, checked: Boolean = false, isEnabled: Boolean = true, onToggle: (Boolean) -> Unit)
    fun SubMenu(label: String, isEnabled: Boolean = true, submenuContent: (TrayMenuBuilder.() -> Unit)?)
    fun Divider()
    fun dispose()
}

