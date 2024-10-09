package com.kdroid.menu

interface TrayMenu {
    fun Item(label: String, onClick: () -> Unit)
    fun SubMenu(label: String, submenuContent: TrayMenu.() -> Unit)
    fun Divider()
}
