package com.kdroid.composetray.menu.api

 /**
  * Interface for building tray menus in a platform-independent manner.
  * Implementations of this interface allow the creation of tray menus
  * with items, checkable items, submenus, and dividers, and provide a
  * mechanism for disposing resources when the menu is no longer needed.
  */
 interface TrayMenuBuilder {
    /**
     * Adds an item to the tray menu.
     *
     * @param label The text label for the menu item.
     * @param isEnabled Indicates whether the menu item is enabled. Defaults to true.
     * @param onClick Lambda function to be invoked when the menu item is clicked. Defaults to an empty lambda.
     */
    fun Item(label: String, isEnabled: Boolean = true, onClick: () -> Unit = {})

    /**
     * Adds a checkable item to the tray menu.
     *
     * @param label The text label for the checkable menu item.
     * @param checked Indicates the initial checked state of the item. Defaults to false.
     * @param isEnabled Determines if the checkable item is enabled. Defaults to true.
     * @param onToggle A lambda function to handle the toggle action. The new checked state is passed as a parameter.
     */
    fun CheckableItem(label: String, checked: Boolean = false, isEnabled: Boolean = true, onToggle: (Boolean) -> Unit)

    /**
     * Adds a submenu to the tray menu.
     *
     * @param label The text label for the submenu.
     * @param isEnabled Indicates whether the submenu is enabled. Defaults to true.
     * @param submenuContent A lambda function defining the contents of the submenu. Can be null.
     */
    fun SubMenu(label: String, isEnabled: Boolean = true, submenuContent: (TrayMenuBuilder.() -> Unit)?)

    /**
     * Adds a visual separator (divider) to the tray menu.
     * This method is used to group or separate menu items, providing better
     * organization and clarity within the menu structure.
     */
    fun Divider()

    /**
     * Disposes of the resources associated with the tray menu.
     * This method should be called when the tray menu is no longer in use
     * to release any system resources held by it.
     */
    fun dispose()
}

