package com.kdroid.composetray.lib.linux

/**
 * Minimal abstraction for Linux tray managers so we can plug different backends (Qt/C++ or Go).
 */
internal interface LinuxTrayController {
    fun addMenuItem(menuItem: LinuxTrayManager.MenuItem)
    fun update(newIconPath: String, newTooltip: String, newOnLeftClick: (() -> Unit)?, newMenuItems: List<LinuxTrayManager.MenuItem>? = null)
    fun startTray()
    fun stopTray()
    fun updateMenuItemCheckedState(label: String, isChecked: Boolean)
}
