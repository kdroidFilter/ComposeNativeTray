package com.kdroid.menu

interface TrayMenu {
    fun Item(label: String, onClick: () -> Unit)
    fun CheckableItem(label: String, onToggle: (Boolean) -> Unit)
    fun SubMenu(label: String, submenuContent: TrayMenu.() -> Unit)
    fun Divider()
}
