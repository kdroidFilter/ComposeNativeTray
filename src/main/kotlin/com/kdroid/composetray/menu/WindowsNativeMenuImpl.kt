package com.kdroid.composetray.menu

import WindowsNativeTrayMenuItem
import com.sun.jna.Memory
import com.sun.jna.Pointer
import java.nio.charset.Charset

class WindowsTrayMenuImpl : TrayMenu {

    private val menuItems = mutableListOf<WindowsNativeTrayMenuItem>()
    private val allocatedMemory = mutableListOf<Any>()

    override fun Item(label: String, isEnabled: Boolean, onClick: () -> Unit) {
        val item = WindowsNativeTrayMenuItem().apply {
            this.text = allocateString(label)
            this.disabled = if (isEnabled) 0 else 1
            this.checked = 0 // Non checkable par défaut
            this.cb = object : WindowsNativeTrayMenuItem.MenuItemCallback {
                override fun invoke(item: WindowsNativeTrayMenuItem) {
                    onClick()
                }
            }
            allocatedMemory.add(this)
        }
        item.write()
        menuItems.add(item)
    }

    override fun CheckableItem(label: String, isEnabled: Boolean, onToggle: (Boolean) -> Unit) {
        val item = WindowsNativeTrayMenuItem().apply {
            this.text = allocateString(label)
            this.disabled = if (isEnabled) 0 else 1
            this.checked = 0 // Décoché par défaut
            this.cb = object : WindowsNativeTrayMenuItem.MenuItemCallback {
                override fun invoke(item: WindowsNativeTrayMenuItem) {
                    item.checked = if (item.checked == 0) 1 else 0
                    onToggle(item.checked == 1)
                    item.write() // Mémoriser les changements
                }
            }
            allocatedMemory.add(this)
        }
        item.write()
        menuItems.add(item)
    }

    override fun SubMenu(label: String, isEnabled: Boolean, submenuContent: TrayMenu.() -> Unit) {
        val subMenuBuilder = WindowsTrayMenuImpl()
        submenuContent(subMenuBuilder) // Exécuter le bloc pour créer le sous-menu
        val subMenu = subMenuBuilder.build()

        val item = WindowsNativeTrayMenuItem().apply {
            this.text = allocateString(label)
            this.disabled = if (isEnabled) 0 else 1
            this.submenu = subMenu[0].pointer // Pointer vers le premier élément du sous-menu
            allocatedMemory.add(this)
        }
        item.write()
        menuItems.add(item)
    }

    override fun Divider() {
        val item = WindowsNativeTrayMenuItem().apply {
            this.text = allocateString("-") // Séparateur
            allocatedMemory.add(this)
        }
        item.write()
        menuItems.add(item)
    }

    override fun dispose() {
        // Nettoyage de la mémoire allouée et des callbacks si nécessaire
        allocatedMemory.clear()
    }

    fun build(): List<WindowsNativeTrayMenuItem> {
        return menuItems
    }

    private fun allocateString(str: String): Pointer {
        val charset = Charset.forName("Windows-1252")
        val byteArray = str.toByteArray(charset)
        val memory = Memory(byteArray.size.toLong() + 1) // +1 pour le terminateur null
        memory.write(0, byteArray, 0, byteArray.size)
        memory.setByte(byteArray.size.toLong(), 0) // Terminateur null
        allocatedMemory.add(memory)
        return memory
    }
}
