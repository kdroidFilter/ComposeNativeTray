package com.kdroid.composetray.lib.linux

import com.sun.jna.Callback
import com.sun.jna.Native
import com.sun.jna.ptr.IntByReference

/**
 * JNA direct mapping to the Go-based systray bridge (linuxlibnew/jna/bridge_linux.go).
 */
internal object LinuxLibTray {
    // Callback types -------------------------------------------------------------
    interface VoidCallback : Callback { fun invoke() }
    interface MenuItemCallback : Callback { fun invoke(menuId: Int) }

    // Library registration logic (simple and consistent with Windows/Mac) --------
    init {
        // Register the native library "systray" for direct calls
        Native.register("systray")
    }

    // Helpers to fetch last click xy
    @JvmStatic external fun Systray_GetLastClickXY(outX: IntByReference, outY: IntByReference)

    // Lifecycle / loop -----------------------------------------------------------
    @JvmStatic external fun Systray_InitCallbacks(ready: VoidCallback?, exit: VoidCallback?, onClick: VoidCallback?, onRClick: VoidCallback?, onMenuItem: MenuItemCallback?)
    @JvmStatic external fun Systray_PrepareExternalLoop()
    @JvmStatic external fun Systray_NativeStart()
    @JvmStatic external fun Systray_NativeEnd()
    @JvmStatic external fun Systray_Quit()

    // Tray properties ------------------------------------------------------------
    @JvmStatic external fun Systray_SetIcon(iconBytes: ByteArray, length: Int)
    @JvmStatic external fun Systray_SetTitle(title: String?)
    @JvmStatic external fun Systray_SetTooltip(tooltip: String?)

    // Menu building --------------------------------------------------------------
    @JvmStatic external fun Systray_ResetMenu()
    @JvmStatic external fun Systray_AddSeparator()
    @JvmStatic external fun Systray_AddMenuItem(title: String?, tooltip: String?): Int
    @JvmStatic external fun Systray_AddMenuItemCheckbox(title: String?, tooltip: String?, checked: Int): Int
    @JvmStatic external fun Systray_AddSubMenuItem(parentID: Int, title: String?, tooltip: String?): Int
    @JvmStatic external fun Systray_AddSubMenuItemCheckbox(parentID: Int, title: String?, tooltip: String?, checked: Int): Int
    @JvmStatic external fun Systray_AddSubMenuSeparator(parentID: Int)

    // Per-item operations --------------------------------------------------------
    @JvmStatic external fun Systray_MenuItem_SetTitle(id: Int, title: String?): Int
    @JvmStatic external fun Systray_MenuItem_Enable(id: Int)
    @JvmStatic external fun Systray_MenuItem_Disable(id: Int)
    @JvmStatic external fun Systray_MenuItem_Show(id: Int)
    @JvmStatic external fun Systray_MenuItem_Hide(id: Int)
    @JvmStatic external fun Systray_MenuItem_Check(id: Int)
    @JvmStatic external fun Systray_MenuItem_Uncheck(id: Int)

    @JvmStatic external fun Systray_SetMenuItemIcon(iconBytes: ByteArray, length: Int, id: Int)
}