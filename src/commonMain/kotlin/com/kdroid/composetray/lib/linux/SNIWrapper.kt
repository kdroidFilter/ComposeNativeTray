package com.kdroid.composetray.lib.linux

import com.sun.jna.*

// JNA Interface for sni_wrapper library
interface SNIWrapper : Library {
    companion object {
        // Load the shared library (adjust the path and name as needed)
        val INSTANCE: SNIWrapper = Native.load("tray", SNIWrapper::class.java) as SNIWrapper
    }

    // Callback interfaces
    interface ActivateCallback : Callback {
        fun invoke(x: Int, y: Int, data: Pointer?)
    }

    interface SecondaryActivateCallback : Callback {
        fun invoke(x: Int, y: Int, data: Pointer?)
    }

    interface ScrollCallback : Callback {
        fun invoke(delta: Int, orientation: Int, data: Pointer?)
    }

    interface ActionCallback : Callback {
        fun invoke(data: Pointer?)
    }

    // System tray initialization and cleanup
    fun init_tray_system(): Int
    fun shutdown_tray_system()

    // Tray creation and destruction
    fun create_tray(id: String?): Pointer?
    fun destroy_handle(handle: Pointer?)

    // Tray property setters
    fun set_title(handle: Pointer?, title: String?)
    fun set_status(handle: Pointer?, status: String?)
    fun set_icon_by_name(handle: Pointer?, name: String?)
    fun set_icon_by_path(handle: Pointer?, path: String?)
    fun update_icon_by_path(handle: Pointer?, path: String?)
    fun set_tooltip_title(handle: Pointer?, title: String?)
    fun set_tooltip_subtitle(handle: Pointer?, subTitle: String?)

    // Menu creation and management
    fun create_menu(): Pointer?
    fun destroy_menu(menu_handle: Pointer?)
    fun set_context_menu(handle: Pointer?, menu: Pointer?)
    fun add_menu_action(menu_handle: Pointer?, text: String?, cb: ActionCallback?, data: Pointer?): Pointer?
    fun add_disabled_menu_action(menu_handle: Pointer?, text: String?, cb: ActionCallback?, data: Pointer?): Pointer?
    fun add_checkable_menu_action(menu_handle: Pointer?, text: String?, checked: Int, cb: ActionCallback?, data: Pointer?): Pointer?
    fun add_menu_separator(menu_handle: Pointer?)
    fun create_submenu(menu_handle: Pointer?, text: String?): Pointer?
    fun set_menu_item_text(menu_item_handle: Pointer?, text: String?)
    fun set_menu_item_enabled(menu_item_handle: Pointer?, enabled: Int)
    fun remove_menu_item(menu_handle: Pointer?, menu_item_handle: Pointer?)

    // Tray event callbacks
    fun set_activate_callback(handle: Pointer?, cb: ActivateCallback?, data: Pointer?)
    fun set_secondary_activate_callback(handle: Pointer?, cb: SecondaryActivateCallback?, data: Pointer?)
    fun set_scroll_callback(handle: Pointer?, cb: ScrollCallback?, data: Pointer?)

    // Notifications
    fun show_notification(handle: Pointer?, title: String?, msg: String?, iconName: String?, secs: Int)

    // Event loop management
    fun sni_exec(): Int
    fun sni_process_events()
    fun sni_stop_exec()
}
// Kotlin example
fun main() {
    // Initialize the tray system
    SNIWrapper.INSTANCE.init_tray_system()

    // Create tray
    val tray = SNIWrapper.INSTANCE.create_tray("my_tray_example")
    if (tray == null) {
        System.err.println("Failed to create tray")
        return
    }

    SNIWrapper.INSTANCE.set_title(tray, "My Tray Example")
    SNIWrapper.INSTANCE.set_status(tray, "Active")
    // Use an icon from a file path (adjust path as needed)
    SNIWrapper.INSTANCE.set_icon_by_path(tray, "/usr/share/icons/hicolor/48x48/apps/openjdk-17.png")
    SNIWrapper.INSTANCE.set_tooltip_title(tray, "My App")
    SNIWrapper.INSTANCE.set_tooltip_subtitle(tray, "Example Tooltip")

    // Set callbacks for tray events
    SNIWrapper.INSTANCE.set_activate_callback(tray, object : SNIWrapper.ActivateCallback {
        override fun invoke(x: Int, y: Int, data: Pointer?) {
            println("Tray activated at ($x, $y)")
        }
    }, null)

    SNIWrapper.INSTANCE.set_secondary_activate_callback(tray, object : SNIWrapper.SecondaryActivateCallback {
        override fun invoke(x: Int, y: Int, data: Pointer?) {
            println("Secondary activate at ($x, $y)")
        }
    }, null)

    SNIWrapper.INSTANCE.set_scroll_callback(tray, object : SNIWrapper.ScrollCallback {
        override fun invoke(delta: Int, orientation: Int, data: Pointer?) {
            println("Scroll: delta=$delta, orientation=$orientation")
        }
    }, null)

    // Create menu
    val menu = SNIWrapper.INSTANCE.create_menu()
    if (menu == null) {
        System.err.println("Failed to create menu")
        SNIWrapper.INSTANCE.destroy_handle(tray)
        SNIWrapper.INSTANCE.shutdown_tray_system()
        return
    }

    // Add a standard action
    SNIWrapper.INSTANCE.add_menu_action(menu, "Action 1", object : SNIWrapper.ActionCallback {
        override fun invoke(data: Pointer?) {
            println("Action 1 clicked!")
        }
    }, null)

    // Add a checkable action
    SNIWrapper.INSTANCE.add_checkable_menu_action(menu, "Toggle Me", 1, object : SNIWrapper.ActionCallback {
        override fun invoke(data: Pointer?) {
            println("Checkable action toggled!")
        }
    }, null)

    // Add separator
    SNIWrapper.INSTANCE.add_menu_separator(menu)

    // Create submenu
    val submenu = SNIWrapper.INSTANCE.create_submenu(menu, "Submenu")
    if (submenu == null) {
        System.err.println("Failed to create submenu")
        SNIWrapper.INSTANCE.destroy_menu(menu)
        SNIWrapper.INSTANCE.destroy_handle(tray)
        SNIWrapper.INSTANCE.shutdown_tray_system()
        return
    }

    // Add actions to submenu
    SNIWrapper.INSTANCE.add_menu_action(submenu, "Submenu Action", object : SNIWrapper.ActionCallback {
        override fun invoke(data: Pointer?) {
            println("Submenu action clicked!")
        }
    }, null)
    SNIWrapper.INSTANCE.add_menu_separator(submenu)
    SNIWrapper.INSTANCE.add_menu_action(submenu, "Action 2", object : SNIWrapper.ActionCallback {
        override fun invoke(data: Pointer?) {
            println("Action 2 clicked!")
        }
    }, null)

    // Add item to change icon dynamically
    SNIWrapper.INSTANCE.add_menu_separator(menu)
    SNIWrapper.INSTANCE.add_menu_action(menu, "Change Icon", object : SNIWrapper.ActionCallback {
        override fun invoke(data: Pointer?) {
            println("Changing icon dynamically!")
            val newIconPath = "/usr/share/icons/hicolor/48x48/apps/firefox.png"  // Example: Firefox icon
            SNIWrapper.INSTANCE.update_icon_by_path(tray, newIconPath)
        }
    }, null)

    // Item that changes name when clicked
    SNIWrapper.INSTANCE.add_menu_separator(menu)
    var changeNameItem: Pointer? = null
    changeNameItem = SNIWrapper.INSTANCE.add_menu_action(menu, "Clique moi pour changer", object : SNIWrapper.ActionCallback {
        override fun invoke(data: Pointer?) {
            println("Changing item name!")
            SNIWrapper.INSTANCE.set_menu_item_text(changeNameItem, "Nouveau Nom")
        }
    }, null)
    if (changeNameItem == null) {
        System.err.println("Failed to create change name item")
        SNIWrapper.INSTANCE.destroy_menu(submenu)
        SNIWrapper.INSTANCE.destroy_menu(menu)
        SNIWrapper.INSTANCE.destroy_handle(tray)
        SNIWrapper.INSTANCE.shutdown_tray_system()
        return
    }

    // Item that adds a new item when clicked
    SNIWrapper.INSTANCE.add_menu_separator(menu)
    val addItemButton = SNIWrapper.INSTANCE.add_menu_action(menu, "Ajoute un item", object : SNIWrapper.ActionCallback {
        override fun invoke(data: Pointer?) {
            println("Adding new item dynamically!")
            SNIWrapper.INSTANCE.add_menu_action(menu, "Nouvel Item Ajouté", null, null)
        }
    }, null)
    if (addItemButton == null) {
        System.err.println("Failed to create add item button")
        SNIWrapper.INSTANCE.destroy_menu(submenu)
        SNIWrapper.INSTANCE.destroy_menu(menu)
        SNIWrapper.INSTANCE.destroy_handle(tray)
        SNIWrapper.INSTANCE.shutdown_tray_system()
        return
    }

    // Item that disappears when clicked
    SNIWrapper.INSTANCE.add_menu_separator(menu)
    var disappearItem: Pointer? = null
    disappearItem = SNIWrapper.INSTANCE.add_menu_action(menu, "Clique moi pour disparaître", object : SNIWrapper.ActionCallback {
        override fun invoke(data: Pointer?) {
            println("Making item disappear!")
            SNIWrapper.INSTANCE.remove_menu_item(menu, disappearItem)
            disappearItem = null  // Reset pointer after removal
        }
    }, null)
    if (disappearItem == null) {
        System.err.println("Failed to create disappear item")
        SNIWrapper.INSTANCE.destroy_menu(submenu)
        SNIWrapper.INSTANCE.destroy_menu(menu)
        SNIWrapper.INSTANCE.destroy_handle(tray)
        SNIWrapper.INSTANCE.shutdown_tray_system()
        return
    }

    // Toggleable item (initially enabled)
    SNIWrapper.INSTANCE.add_menu_separator(menu)
    var toggleItem = SNIWrapper.INSTANCE.add_menu_action(menu, "Item à toggler", object : SNIWrapper.ActionCallback {
        override fun invoke(data: Pointer?) {
            println("Toggle item clicked!")
        }
    }, null)
    if (toggleItem == null) {
        System.err.println("Failed to create toggle item")
        SNIWrapper.INSTANCE.destroy_menu(submenu)
        SNIWrapper.INSTANCE.destroy_menu(menu)
        SNIWrapper.INSTANCE.destroy_handle(tray)
        SNIWrapper.INSTANCE.shutdown_tray_system()
        return
    }

    // Submenu for enabling/disabling the item
    SNIWrapper.INSTANCE.add_menu_separator(menu)
    val toggleSubmenu = SNIWrapper.INSTANCE.create_submenu(menu, "Toggle Item")
    if (toggleSubmenu == null) {
        System.err.println("Failed to create toggle submenu")
        SNIWrapper.INSTANCE.destroy_menu(submenu)
        SNIWrapper.INSTANCE.destroy_menu(menu)
        SNIWrapper.INSTANCE.destroy_handle(tray)
        SNIWrapper.INSTANCE.shutdown_tray_system()
        return
    }

    SNIWrapper.INSTANCE.add_menu_action(toggleSubmenu, "Activer", object : SNIWrapper.ActionCallback {
        override fun invoke(data: Pointer?) {
            println("Enabling item!")
            SNIWrapper.INSTANCE.set_menu_item_enabled(toggleItem, 1)
        }
    }, null)

    SNIWrapper.INSTANCE.add_menu_action(toggleSubmenu, "Désactiver", object : SNIWrapper.ActionCallback {
        override fun invoke(data: Pointer?) {
            println("Disabling item!")
            SNIWrapper.INSTANCE.set_menu_item_enabled(toggleItem, 0)
        }
    }, null)

    // Add a disabled item
    SNIWrapper.INSTANCE.add_menu_separator(menu)
    val disabledItem = SNIWrapper.INSTANCE.add_disabled_menu_action(menu, "Item Disabled", null, null)
    if (disabledItem == null) {
        System.err.println("Failed to create disabled item")
        SNIWrapper.INSTANCE.destroy_menu(toggleSubmenu)
        SNIWrapper.INSTANCE.destroy_menu(submenu)
        SNIWrapper.INSTANCE.destroy_menu(menu)
        SNIWrapper.INSTANCE.destroy_handle(tray)
        SNIWrapper.INSTANCE.shutdown_tray_system()
        return
    }

    // Set context menu
    SNIWrapper.INSTANCE.set_context_menu(tray, menu)

    // Run the event loop (blocking)
    println("Tray is running. Press Ctrl+C to exit.")
    SNIWrapper.INSTANCE.sni_exec()

    // Cleanup
    SNIWrapper.INSTANCE.destroy_menu(toggleSubmenu)
    SNIWrapper.INSTANCE.destroy_menu(submenu)
    SNIWrapper.INSTANCE.destroy_menu(menu)
    SNIWrapper.INSTANCE.destroy_handle(tray)
    SNIWrapper.INSTANCE.shutdown_tray_system()
}