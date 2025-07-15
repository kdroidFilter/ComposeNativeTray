package com.kdroid.composetray.menu.impl

import com.kdroid.composetray.lib.linux.libtray.LinuxNativeTrayMenuItem
import com.kdroid.composetray.menu.api.TrayMenuBuilder
import com.sun.jna.Pointer
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal class LinuxLibTrayMenuBuilderImpl(
    private val iconPath: String,
    private val tooltip: String = "",
    private val onLeftClick: (() -> Unit)?
) : TrayMenuBuilder {

    private val menuItems = mutableListOf<LinuxNativeTrayMenuItem>()
    private val lock = ReentrantLock()

    // Prevent GC
    private val persistentMenuItems = mutableListOf<LinuxNativeTrayMenuItem>()
    private val persistentCallbacks = mutableListOf<LinuxNativeTrayMenuItem.MenuItemCallback>()

    override fun Item(label: String, isEnabled: Boolean, onClick: () -> Unit) {
        lock.withLock {
            val menuItem = LinuxNativeTrayMenuItem().apply {
                text = label
                disabled = if (isEnabled) 0 else 1
                checked = -1 // ❌ not checkable

                val callback = LinuxNativeTrayMenuItem.MenuItemCallback { _ ->
                    onClick()
                }
                cb = callback
                persistentCallbacks.add(callback)
            }
            menuItems.add(menuItem)
            persistentMenuItems.add(menuItem)
        }
    }

    override fun CheckableItem(
        label: String,
        checked: Boolean,
        isEnabled: Boolean,
        onToggle: (Boolean) -> Unit
    ) {
        var isChecked = checked

        lock.withLock {
            val menuItem = LinuxNativeTrayMenuItem().apply {
                text = label
                disabled = if (isEnabled) 0 else 1
                this.checked = if (isChecked) 1 else 0 // 1 / 0 → checkable

                val callback = LinuxNativeTrayMenuItem.MenuItemCallback { item ->
                    isChecked = !isChecked
                    item.checked = if (isChecked) 1 else 0
                    onToggle(isChecked)
                }
                cb = callback
                persistentCallbacks.add(callback)
            }
            menuItems.add(menuItem)
            persistentMenuItems.add(menuItem)
        }
    }

    override fun SubMenu(
        label: String,
        isEnabled: Boolean,
        submenuContent: (TrayMenuBuilder.() -> Unit)?
    ) {
        if (submenuContent == null) return

        val subMenuBuilder = LinuxLibTrayMenuBuilderImpl(iconPath, tooltip, onLeftClick)
        submenuContent.invoke(subMenuBuilder)

        lock.withLock {
            val menuItem = LinuxNativeTrayMenuItem().apply {
                text = label
                disabled = if (isEnabled) 0 else 1
                checked = -1 // ❌ not checkable
                submenu = subMenuBuilder.build()
            }
            menuItems.add(menuItem)
            persistentMenuItems.add(menuItem)
        }
    }

    override fun Divider() {
        lock.withLock {
            val divider = LinuxNativeTrayMenuItem().apply {
                text = "-"
                disabled = 0
                checked = -1 // ❌ not checkable
            }
            menuItems.add(divider)
            persistentMenuItems.add(divider)
        }
    }

    override fun dispose() {
        lock.withLock {
            persistentMenuItems.clear()
            persistentCallbacks.clear()
            menuItems.clear()
        }
    }

    fun build(): Pointer? = lock.withLock {
        if (menuItems.isEmpty()) return null

        // +1 for NULL terminator
        val menuArray =
            LinuxNativeTrayMenuItem().toArray(menuItems.size + 1) as Array<LinuxNativeTrayMenuItem>

        menuItems.forEachIndexed { index, item ->
            menuArray[index].apply {
                text = item.text
                disabled = item.disabled
                checked = item.checked
                cb = item.cb
                submenu = item.submenu
                write()
            }
        }

        // NULL terminator
        menuArray[menuItems.size].apply {
            text = null
            write()
        }
        menuArray[0].pointer
    }
}
