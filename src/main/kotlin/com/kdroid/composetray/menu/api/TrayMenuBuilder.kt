package com.kdroid.composetray.menu.api

interface TrayMenuBuilder {
    fun Item(label: String, isEnabled: Boolean = true, onClick: () -> Unit = {})
    fun CheckableItem(label: String, isEnabled: Boolean = true, onToggle: (Boolean) -> Unit)
    fun SubMenu(label: String, isEnabled: Boolean = true, submenuContent: TrayMenuBuilder.() -> Unit)
    fun Divider()
    fun dispose()
}

