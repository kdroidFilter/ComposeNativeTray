package com.kdroid.composetray.lib.windows
import androidx.compose.runtime.mutableStateOf
import com.sun.jna.Native
import java.util.concurrent.atomic.AtomicBoolean

internal class WindowsTrayManager(
    iconPath: String,
    tooltip: String = "",
    onLeftClick: (() -> Unit)? = null
) {
    private val trayLib: WindowsNativeTrayLibrary = Native.load("tray", WindowsNativeTrayLibrary::class.java)
    private val tray: WindowsNativeTray = WindowsNativeTray()
    private val menuItems: MutableList<MenuItem> = mutableListOf()
    private val running = AtomicBoolean(true)

    // Maintain a reference to all callbacks to avoid GC
    private val callbackReferences: MutableList<StdCallCallback> = mutableListOf()
    private val nativeMenuItemsReferences: MutableList<WindowsNativeTrayMenuItem> = mutableListOf()
    private val onLeftClickCallback = mutableStateOf(onLeftClick)

    init {
        tray.icon_filepath = iconPath
        tray.tooltip = tooltip
    }

    // Top level MenuItem class
    data class MenuItem(
        val text: String,
        val isEnabled: Boolean = true,
        val isCheckable: Boolean = false,
        val isChecked: Boolean = false,
        val onClick: (() -> Unit)? = null,
        val subMenuItems: List<MenuItem> = emptyList()
    )

    fun addMenuItem(menuItem: MenuItem) {
        synchronized(menuItems) {
            menuItems.add(menuItem)
        }
    }

    // Start the tray
    fun startTray() {
        synchronized(tray) {
            initializeOnLeftClickCallback()
            if (tray.menu == null) {
                initializeTrayMenu()
                require(trayLib.tray_init(tray) == 0) { "Échec de l'initialisation du tray ${trayLib.tray_init(tray)}" }
                runTrayLoop()
            }
        }
    }

    private fun initializeOnLeftClickCallback() {
        if (onLeftClickCallback.value != null) {
            tray.cb = WindowsNativeTray.TrayCallback {
                onLeftClickCallback.value?.invoke()
            }
        }
    }

    private fun initializeTrayMenu() {
        callbackReferences.clear()
        nativeMenuItemsReferences.clear()
        val menuItemPrototype = WindowsNativeTrayMenuItem()
        val nativeMenuItems = menuItemPrototype.toArray(menuItems.size + 1) as Array<WindowsNativeTrayMenuItem>

        synchronized(menuItems) {
            menuItems.forEachIndexed { index, item ->
                val nativeItem = nativeMenuItems[index]
                initializeNativeMenuItem(nativeItem, item)
                nativeItem.write()
                nativeMenuItemsReferences.add(nativeItem) // Store reference to prevent GC
            }
        }

        // Last element to mark the end of the menu
        nativeMenuItems[menuItems.size].text = null
        nativeMenuItems[menuItems.size].write()

        tray.menu = nativeMenuItems[0].pointer
    }


    private fun initializeNativeMenuItem(nativeItem: WindowsNativeTrayMenuItem, menuItem: MenuItem) {
        nativeItem.text = menuItem.text
        nativeItem.disabled = if (menuItem.isEnabled) 0 else 1
        nativeItem.checked = if (menuItem.isChecked) 1 else 0

        // Create the menu item callback
        menuItem.onClick?.let { onClick ->
            val callback = StdCallCallback { item ->
                synchronized(tray) {
                    if (running.get() && tray.menu != null) {
                        onClick()
                        if (menuItem.isCheckable) {
                            item.checked = if (item.checked == 0) 1 else 0
                            item.write()
                            trayLib.tray_update(tray)
                        }
                    }
                }
            }
            nativeItem.cb = callback
            callbackReferences.add(callback) // Store reference to prevent GC
        }

        // If the element has child elements
        if (menuItem.subMenuItems.isNotEmpty()) {
            val subMenuPrototype = WindowsNativeTrayMenuItem()
            val subMenuItemsArray =
                subMenuPrototype.toArray(menuItem.subMenuItems.size + 1) as Array<WindowsNativeTrayMenuItem>
            menuItem.subMenuItems.forEachIndexed { index, subItem ->
                initializeNativeMenuItem(subMenuItemsArray[index], subItem)
                subMenuItemsArray[index].write()
                nativeMenuItemsReferences.add(subMenuItemsArray[index]) // Store reference to prevent GC
            }
            // End marker
            subMenuItemsArray[menuItem.subMenuItems.size].text = null
            subMenuItemsArray[menuItem.subMenuItems.size].write()
            nativeItem.submenu = subMenuItemsArray[0].pointer
        }

    }

    //Tray loop
    private fun runTrayLoop() {
        try {
            while (running.get()) {
                if (trayLib.tray_loop(1) != 0) break
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            synchronized(tray) {
                trayLib.tray_exit()
              //  tray.menu?.let { trayLib.tray_free_menu(it) }
            }
        }
    }

    fun updateTooltip(text: String) {
        synchronized(tray) {
            tray.tooltip = text
            tray.write()
            trayLib.tray_update(tray)
        }
    }

    fun updateIcon(path: String) {
        synchronized(tray) {
            tray.icon_filepath = path
            tray.write()
            trayLib.tray_update(tray)
        }
    }

    fun updateMenuItems(items: List<MenuItem>) {
        synchronized(tray) {
            menuItems.clear()
            menuItems.addAll(items)
            initializeTrayMenu()
            tray.write()
            trayLib.tray_update(tray)
        }
    }

    fun stopTray() {
        running.set(false)
        trayLib.tray_exit()
    }
}
