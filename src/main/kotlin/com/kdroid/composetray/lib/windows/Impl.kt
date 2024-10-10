import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import java.nio.charset.Charset

fun main() {
    // Charger la bibliothèque DLL
    val trayLib = Native.load("tray", windowsNativeTrayLibrary::class.java) as windowsNativeTrayLibrary

    // Garder les références pour éviter le garbage collection
    val allocatedMemory = mutableListOf<Any>()

    // Fonction pour allouer de la mémoire pour une chaîne de caractères
    fun allocateString(str: String?): Pointer? {
        if (str == null) return null
        val charset: Charset = Charset.forName("Windows-1252")
        val byteArray = str.toByteArray(charset)
        val memory = Memory(byteArray.size.toLong() + 1) // +1 pour le terminateur null
        memory.write(0, byteArray, 0, byteArray.size)
        memory.setByte(byteArray.size.toLong(), 0.toByte()) // Terminateur null
        allocatedMemory.add(memory)
        return memory
    }

    // Initialiser le tray ici pour qu'il soit accessible dans les callbacks
    val tray = WindowsNativeTray()

    // Créer un prototype pour les éléments du menu principal
    val menuItemPrototype = WindowsNativeTrayMenuItem()

    // Créer un tableau de TrayMenuItem pour le menu principal
    val menuItemsArray = menuItemPrototype.toArray(6) as Array<WindowsNativeTrayMenuItem>

    // Créer les sous-menus
    val submenuItemPrototype = WindowsNativeTrayMenuItem()
    val submenuItemsArray = submenuItemPrototype.toArray(2) as Array<WindowsNativeTrayMenuItem>

    // Initialiser le premier élément du sous-menu
    submenuItemsArray[0].apply {
        text = allocateString("Sous-élément 1")
        cb = object : WindowsNativeTrayMenuItem.MenuItemCallback {
            override fun invoke(item: WindowsNativeTrayMenuItem) {
                println("Sous-élément 1 cliqué")
            }
        }.also { allocatedMemory.add(it) }
        disabled = 0
        checked = 0
        submenu = null
        allocatedMemory.add(this)
        write()
    }

    // Initialiser le dernier élément du sous-menu (élément de fin)
    submenuItemsArray[1].apply {
        text = null // Indique la fin du sous-menu
        cb = null
        disabled = 0
        checked = 0
        submenu = null
        allocatedMemory.add(this)
        write()
    }

    // Initialiser le premier élément du menu principal avec un sous-menu
    menuItemsArray[0].apply {
        text = allocateString("Élément 1")
        cb = null // Pas de callback car cet élément a un sous-menu
        disabled = 0
        checked = 0
        submenu = submenuItemsArray[0].pointer // Pointeur vers le premier élément du sous-menu
        allocatedMemory.add(this)
        write()
    }

    // Ajouter un séparateur
    menuItemsArray[1].apply {
        text = allocateString("-") // Séparateur
        cb = null
        disabled = 0
        checked = 0
        submenu = null
        allocatedMemory.add(this)
        write()
    }

    // Ajouter un élément désactivé
    menuItemsArray[2].apply {
        text = allocateString("Élément désactivé")
        cb = object : WindowsNativeTrayMenuItem.MenuItemCallback {
            override fun invoke(item: WindowsNativeTrayMenuItem) {
                println("Cet élément est désactivé")
            }
        }.also { allocatedMemory.add(it) }
        disabled = 1 // Désactivé
        checked = 0
        submenu = null
        allocatedMemory.add(this)
        write()
    }

    // Ajouter un élément cochable
    menuItemsArray[3].apply {
        text = allocateString("Élément cochable")
        cb = object : WindowsNativeTrayMenuItem.MenuItemCallback {
            override fun invoke(item: WindowsNativeTrayMenuItem) {
                // Basculer l'état coché
                item.checked = if (item.checked == 0) 1 else 0
                println("Élément cochable cliqué, nouvel état: ${item.checked}")
                item.write() // Écrire les changements en mémoire native
                // Mettre à jour le tray pour refléter le changement
                trayLib.tray_update(tray)
            }
        }.also { allocatedMemory.add(it) }
        disabled = 0
        checked = 0 // Initialement non coché
        submenu = null
        allocatedMemory.add(this)
        write()
    }

    // Ajouter un autre élément normal
    menuItemsArray[4].apply {
        text = allocateString("Élément 2")
        cb = object : WindowsNativeTrayMenuItem.MenuItemCallback {
            override fun invoke(item: WindowsNativeTrayMenuItem) {
                println("Élément 2 cliqué")
            }
        }.also { allocatedMemory.add(it) }
        disabled = 0
        checked = 0
        submenu = null
        allocatedMemory.add(this)
        write()
    }

    // Initialiser le dernier élément du menu principal (élément de fin)
    menuItemsArray[5].apply {
        text = null // Indique la fin du menu principal
        cb = null
        disabled = 0
        checked = 0
        submenu = null
        allocatedMemory.add(this)
        write()
    }

    // Maintenant, initialiser les propriétés du tray
    tray.apply {
        icon_filepath = allocateString("C:\\Users\\Eyahou Gambache\\CLionProjects\\tray\\icon.ico")
        tooltip = allocateString("Mon application Kotlin Tray")
        cb = object : WindowsNativeTray.TrayCallback {
            override fun invoke(tray: WindowsNativeTray) {
                println("Icône du tray cliquée")
            }
        }.also { allocatedMemory.add(it) }
        menu = menuItemsArray[0].pointer // Pointeur vers le premier élément du menu principal
    }

    allocatedMemory.add(tray)
    tray.write()

    // Démarrer le tray
    try {
        val initResult = trayLib.tray_init(tray)
        if (initResult != 0) {
            println("Échec de l'initialisation du tray")
            return
        }

        // Boucle du tray
        while (true) {
            val loopResult = trayLib.tray_loop(1)
            if (loopResult != 0) {
                break
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    } finally {
        // Nettoyage
        trayLib.tray_exit()
    }
}
