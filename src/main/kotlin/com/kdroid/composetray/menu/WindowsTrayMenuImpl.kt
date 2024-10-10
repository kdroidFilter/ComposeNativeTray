package com.kdroid.composetray.menu

import WindowsNativeTrayMenuItem
import allocateString
import com.sun.jna.Memory
import initializeTray
import loadLibrary
import startTray

class WindowsTrayMenuImpl : TrayMenu {
    private val trayLib = loadLibrary()
    private val allocatedMemory = mutableListOf<Any>()
    private val tray = initializeTray(trayLib, allocatedMemory)

    private val menuItems = mutableListOf<WindowsNativeTrayMenuItem>()


    fun build() {
        // Associer le premier item du menu au tray
        if (menuItems.isNotEmpty()) {
            tray.menu = menuItems[0].pointer
        }
        tray.write()
        startTray(trayLib, tray)
    }

    override fun Item(label: String, isEnabled: Boolean, onClick: () -> Unit) {
        val menuItem = WindowsNativeTrayMenuItem().apply {
            this.text = allocateString(label, allocatedMemory)
            this.disabled = if (isEnabled) 0 else 1
            this.cb = object : WindowsNativeTrayMenuItem.MenuItemCallback {
                override fun invoke(item: WindowsNativeTrayMenuItem) {
                    onClick()
                }
            }.also { allocatedMemory.add(it) }
        }
        allocatedMemory.add(menuItem)
        menuItem.write()
        menuItems.add(menuItem)
    }

    override fun CheckableItem(label: String, isEnabled: Boolean, onToggle: (Boolean) -> Unit) {
        val menuItem = WindowsNativeTrayMenuItem().apply {
            this.text = allocateString(label, allocatedMemory)
            this.disabled = if (isEnabled) 0 else 1
            this.checked = 0 // Par défaut non coché
            this.cb = object : WindowsNativeTrayMenuItem.MenuItemCallback {
                override fun invoke(item: WindowsNativeTrayMenuItem) {
                    item.checked = if (item.checked == 0) 1 else 0
                    item.write()
                    onToggle(item.checked == 1)
                }
            }.also { allocatedMemory.add(it) }
        }
        allocatedMemory.add(menuItem)
        menuItem.write()
        menuItems.add(menuItem)
    }

    override fun SubMenu(label: String, isEnabled: Boolean, submenuContent: TrayMenu.() -> Unit) {
        val submenuItems = mutableListOf<WindowsNativeTrayMenuItem>()

        val submenuTray = object : TrayMenu {
            override fun Item(label: String, isEnabled: Boolean, onClick: () -> Unit) {
                val subItem = WindowsNativeTrayMenuItem().apply {
                    this.text = allocateString(label, allocatedMemory)
                    this.disabled = if (isEnabled) 0 else 1
                    this.cb = object : WindowsNativeTrayMenuItem.MenuItemCallback {
                        override fun invoke(item: WindowsNativeTrayMenuItem) {
                            onClick()
                        }
                    }.also { allocatedMemory.add(it) }
                }
                allocatedMemory.add(subItem)
                subItem.write()
                submenuItems.add(subItem)
            }

            override fun CheckableItem(label: String, isEnabled: Boolean, onToggle: (Boolean) -> Unit) {
                val subItem = WindowsNativeTrayMenuItem().apply {
                    this.text = allocateString(label, allocatedMemory)
                    this.disabled = if (isEnabled) 0 else 1
                    this.checked = 0 // Par défaut non coché
                    this.cb = object : WindowsNativeTrayMenuItem.MenuItemCallback {
                        override fun invoke(item: WindowsNativeTrayMenuItem) {
                            item.checked = if (item.checked == 0) 1 else 0
                            item.write()
                            onToggle(item.checked == 1)
                        }
                    }.also { allocatedMemory.add(it) }
                }
                allocatedMemory.add(subItem)
                subItem.write()
                submenuItems.add(subItem)
            }

            override fun SubMenu(label: String, isEnabled: Boolean, submenuContent: TrayMenu.() -> Unit) {
                // Appels récursifs pour les sous-menus
            }

            override fun Divider() {
                val divider = WindowsNativeTrayMenuItem().apply {
                    this.text = null
                }
                allocatedMemory.add(divider)
                divider.write()
                submenuItems.add(divider)
            }

            override fun dispose() {
                // Pas d'implémentation nécessaire ici
            }
        }

        // Remplir le sous-menu
        submenuTray.submenuContent()

        // Ajouter le sous-menu au menu principal
        val menuItem = WindowsNativeTrayMenuItem().apply {
            this.text = allocateString(label, allocatedMemory)
            this.disabled = if (isEnabled) 0 else 1
            this.submenu = submenuItems[0].pointer
        }
        allocatedMemory.add(menuItem)
        menuItem.write()
        menuItems.add(menuItem)
    }

    override fun Divider() {
        val divider = WindowsNativeTrayMenuItem().apply {
            this.text = null // Un séparateur n'a pas de texte
        }
        allocatedMemory.add(divider)
        divider.write()
        menuItems.add(divider)
    }

    override fun dispose() {
        // Libération de la mémoire
        allocatedMemory.forEach {
            if (it is Memory) {
                it.clear()
            }
        }
        allocatedMemory.clear()
    }
}
