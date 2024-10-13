package com.kdroid.composetray.lib.windows

import WindowsNativeTray
import WindowsNativeTrayLibrary
import WindowsNativeTrayMenuItem
import com.kdroid.composetray.callbacks.windows.MenuItemCallback
import com.sun.jna.Native
import java.util.concurrent.atomic.AtomicBoolean

class WindowsTrayManager(iconPath : String, tooltip : String = "") {
    private val trayLib: WindowsNativeTrayLibrary = Native.load("tray", WindowsNativeTrayLibrary::class.java)
    private val tray: WindowsNativeTray = WindowsNativeTray()
    private val menuItems: MutableList<MenuItem> = mutableListOf()
    private val running = AtomicBoolean(true)

    init {
        tray.icon_filepath = iconPath
        tray.tooltip = tooltip
    }

    // Classe MenuItem de haut niveau
    data class MenuItem(
        val text: String,
        val isEnabled: Boolean = true,
        val isCheckable: Boolean = false,
        val isChecked: Boolean = false,
        val onClick: (() -> Unit)? = null,
        val subMenuItems: List<MenuItem> = emptyList()
    )

    // Ajouter un élément de menu au tray
    fun addMenuItem(menuItem: MenuItem) {
        menuItems.add(menuItem)
    }

    // Démarrer le tray
    fun startTray() {
        initializeTrayMenu()
        require(trayLib.tray_init(tray) == 0) { "Échec de l'initialisation du tray" }
        runTrayLoop()
    }

    // Initialisation des éléments de menu
    private fun initializeTrayMenu() {
        val menuItemPrototype = WindowsNativeTrayMenuItem()
        val nativeMenuItems = menuItemPrototype.toArray(menuItems.size + 1) as Array<WindowsNativeTrayMenuItem>

        menuItems.forEachIndexed { index, item ->
            val nativeItem = nativeMenuItems[index]
            initializeNativeMenuItem(nativeItem, item)
            nativeItem.write()
        }

        // Dernier élément pour marquer la fin du menu
        nativeMenuItems[menuItems.size].text = null
        nativeMenuItems[menuItems.size].write()

        tray.menu = nativeMenuItems[0].pointer
    }

    // Initialisation d'un élément de menu natif
    private fun initializeNativeMenuItem(nativeItem: WindowsNativeTrayMenuItem, menuItem: MenuItem) {
        nativeItem.text = menuItem.text
        nativeItem.disabled = if (menuItem.isEnabled) 0 else 1
        nativeItem.checked = if (menuItem.isChecked) 1 else 0

        // Création du callback de l'élément de menu
        menuItem.onClick?.let { onClick ->
            val callback = MenuItemCallback { item ->
                onClick()
                if (menuItem.isCheckable) {
                    item.checked = if (item.checked == 0) 1 else 0
                    item.write()
                    trayLib.tray_update(tray)
                }
            }
            nativeItem.cb = callback
        }

        // Si l'élément a des sous-éléments
        if (menuItem.subMenuItems.isNotEmpty()) {
            val subMenuPrototype = WindowsNativeTrayMenuItem()
            val subMenuItemsArray = subMenuPrototype.toArray(menuItem.subMenuItems.size + 1) as Array<WindowsNativeTrayMenuItem>
            menuItem.subMenuItems.forEachIndexed { index, subItem ->
                initializeNativeMenuItem(subMenuItemsArray[index], subItem)
                subMenuItemsArray[index].write()
            }
            // Marqueur de fin
            subMenuItemsArray[menuItem.subMenuItems.size].text = null
            subMenuItemsArray[menuItem.subMenuItems.size].write()
            nativeItem.submenu = subMenuItemsArray[0].pointer
        }
    }

    // Boucle du tray
    private fun runTrayLoop() {
        try {
            while (running.get()) {
                if (trayLib.tray_loop(1) != 0) break
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            trayLib.tray_exit()
            tray.menu?.let { trayLib.tray_free_menu(it) }
        }
    }

    // Arrêter le tray
    fun stopTray() {
        running.set(false)
    }
}

