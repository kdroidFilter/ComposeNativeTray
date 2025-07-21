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
    * This follows Compose's idiomatic pattern for stateful components.
    *
    * @param label The text label for the checkable menu item.
    * @param checked The current checked state of the item.
    * @param onCheckedChange A lambda function called when the user toggles the item. The new checked state is passed as a parameter.
    * @param isEnabled Determines if the checkable item is enabled. Defaults to true.
    */
   fun CheckableItem(
      label: String,
      checked: Boolean,
      onCheckedChange: (Boolean) -> Unit,
      isEnabled: Boolean = true
   )

   /**
    * Adds a checkable item to the tray menu with the legacy API.
    * @deprecated Use the new API with separate checked and onCheckedChange parameters for better Compose idiomaticity
    */
   @Deprecated(
      message = "Use CheckableItem with separate checked and onCheckedChange parameters",
      replaceWith = ReplaceWith("CheckableItem(label, checked, onCheckedChange, isEnabled)")
   )
   fun CheckableItem(
      label: String,
      checked: Boolean = false,
      isEnabled: Boolean = true,
      onToggle: (Boolean) -> Unit
   ) {
      // Delegate to the new API
      CheckableItem(label, checked, onToggle, isEnabled)
   }

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