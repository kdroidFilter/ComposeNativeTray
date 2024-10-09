package com.kdroid.menu

interface TrayMenu {
    fun Item(label: String, isEnabled: Boolean = true, onClick: () -> Unit)
    fun CheckableItem(label: String, isEnabled: Boolean = true, onToggle: (Boolean) -> Unit)
    fun SubMenu(label: String, isEnabled: Boolean = true, submenuContent: TrayMenu.() -> Unit)
    fun Divider()
    fun dispose()
}

