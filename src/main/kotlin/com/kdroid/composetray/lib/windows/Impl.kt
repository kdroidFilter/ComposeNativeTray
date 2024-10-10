import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import java.nio.charset.Charset

fun main() {
    val trayLib = loadLibrary()
    val allocatedMemory = mutableListOf<Any>()

    val tray = initializeTray(trayLib, allocatedMemory)
    val menuItems = createMainMenu(allocatedMemory)
    tray.menu = menuItems[0].pointer

    tray.write()

    startTray(trayLib, tray)
}

fun loadLibrary(): WindowsNativeTrayLibrary {
    return Native.load("tray", WindowsNativeTrayLibrary::class.java) as WindowsNativeTrayLibrary
}

fun initializeTray(trayLib: WindowsNativeTrayLibrary, allocatedMemory: MutableList<Any>): WindowsNativeTray {
    val tray = WindowsNativeTray()
    tray.apply {
        icon_filepath = allocateString("C:\\Users\\Eyahou Gambache\\CLionProjects\\tray\\icon.ico", allocatedMemory)
        tooltip = allocateString("Mon application Kotlin Tray", allocatedMemory)
        cb = createTrayCallback(allocatedMemory)
    }
    allocatedMemory.add(tray)
    return tray
}

fun createTrayCallback(allocatedMemory: MutableList<Any>): WindowsNativeTray.TrayCallback {
    return object : WindowsNativeTray.TrayCallback {
        override fun invoke(tray: WindowsNativeTray) {
            println("Icône du tray cliquée")
        }
    }.also { allocatedMemory.add(it) }
}

fun createMainMenu(allocatedMemory: MutableList<Any>): Array<WindowsNativeTrayMenuItem> {
    val menuItemPrototype = WindowsNativeTrayMenuItem()
    val menuItemsArray = menuItemPrototype.toArray(6) as Array<WindowsNativeTrayMenuItem>

    val submenuItems = createSubMenu(allocatedMemory)

    initializeMenuItem(menuItemsArray[0], "Élément 1", null, 0, 0, submenuItems[0].pointer, allocatedMemory)
    initializeMenuItem(menuItemsArray[1], "-", null, 0, 0, null, allocatedMemory)
    initializeMenuItem(menuItemsArray[2], "Élément désactivé", createDisabledItemCallback(allocatedMemory), 1, 0, null, allocatedMemory)
    initializeMenuItem(menuItemsArray[3], "Élément cochable", createCheckableItemCallback(allocatedMemory), 0, 0, null, allocatedMemory)
    initializeMenuItem(menuItemsArray[4], "Élément 2", createNormalItemCallback(allocatedMemory), 0, 0, null, allocatedMemory)
    initializeMenuItem(menuItemsArray[5], null, null, 0, 0, null, allocatedMemory)

    return menuItemsArray
}

fun createSubMenu(allocatedMemory: MutableList<Any>): Array<WindowsNativeTrayMenuItem> {
    val submenuItemPrototype = WindowsNativeTrayMenuItem()
    val submenuItemsArray = submenuItemPrototype.toArray(2) as Array<WindowsNativeTrayMenuItem>

    initializeMenuItem(submenuItemsArray[0], "Sous-élément 1", createSubMenuItemCallback(allocatedMemory), 0, 0, null, allocatedMemory)
    initializeMenuItem(submenuItemsArray[1], null, null, 0, 0, null, allocatedMemory)

    return submenuItemsArray
}

fun initializeMenuItem(item: WindowsNativeTrayMenuItem, text: String?, cb: WindowsNativeTrayMenuItem.MenuItemCallback?, disabled: Int, checked: Int, submenu: Pointer?, allocatedMemory: MutableList<Any>) {
    item.apply {
        this.text = allocateString(text, allocatedMemory)
        this.cb = cb
        this.disabled = disabled
        this.checked = checked
        this.submenu = submenu
        allocatedMemory.add(this)
        write()
    }
}

fun createSubMenuItemCallback(allocatedMemory: MutableList<Any>): WindowsNativeTrayMenuItem.MenuItemCallback {
    return object : WindowsNativeTrayMenuItem.MenuItemCallback {
        override fun invoke(item: WindowsNativeTrayMenuItem) {
            println("Sous-élément 1 cliqué")
        }
    }.also { allocatedMemory.add(it) }
}

fun createDisabledItemCallback(allocatedMemory: MutableList<Any>): WindowsNativeTrayMenuItem.MenuItemCallback {
    return object : WindowsNativeTrayMenuItem.MenuItemCallback {
        override fun invoke(item: WindowsNativeTrayMenuItem) {
            println("Cet élément est désactivé")
        }
    }.also { allocatedMemory.add(it) }
}

fun createCheckableItemCallback(allocatedMemory: MutableList<Any>): WindowsNativeTrayMenuItem.MenuItemCallback {
    return object : WindowsNativeTrayMenuItem.MenuItemCallback {
        override fun invoke(item: WindowsNativeTrayMenuItem) {
            item.checked = if (item.checked == 0) 1 else 0
            println("Élément cochable cliqué, nouvel état: ${item.checked}")
            item.write()
        }
    }.also { allocatedMemory.add(it) }
}

fun createNormalItemCallback(allocatedMemory: MutableList<Any>): WindowsNativeTrayMenuItem.MenuItemCallback {
    return object : WindowsNativeTrayMenuItem.MenuItemCallback {
        override fun invoke(item: WindowsNativeTrayMenuItem) {
            println("Élément 2 cliqué")
        }
    }.also { allocatedMemory.add(it) }
}

fun allocateString(str: String?, allocatedMemory: MutableList<Any>): Pointer? {
    if (str == null) return null
    val charset: Charset = Charset.forName("Windows-1252")
    val byteArray = str.toByteArray(charset)
    val memory = Memory(byteArray.size.toLong() + 1)
    memory.write(0, byteArray, 0, byteArray.size)
    memory.setByte(byteArray.size.toLong(), 0.toByte())
    allocatedMemory.add(memory)
    return memory
}

fun startTray(trayLib: WindowsNativeTrayLibrary, tray: WindowsNativeTray) {
    try {
        val initResult = trayLib.tray_init(tray)
        if (initResult != 0) {
            println("Échec de l'initialisation du tray")
            return
        }

        while (true) {
            val loopResult = trayLib.tray_loop(1)
            if (loopResult != 0) {
                break
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    } finally {
        trayLib.tray_exit()
    }
}