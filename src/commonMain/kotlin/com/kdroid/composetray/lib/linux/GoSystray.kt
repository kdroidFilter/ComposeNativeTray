package com.kdroid.composetray.lib.linux

import com.sun.jna.Callback
import com.sun.jna.Library
import com.sun.jna.Native
import java.io.File

/**
 * JNA interface to the Go-based systray bridge (linuxlibnew/jna/bridge_linux.go).
 *
 * We try to load a shared library named "systray_jna" first, then fallback to "systray".
 * You can override via system properties:
 * -Dcomposetray.native.lib=/abs/path/to/libsystray_jna.so
 * -Dcomposetray.native.lib.path=/abs/dir/containing/lib
 */
internal interface GoSystray : Library {
    companion object {
        private fun tryLoadDirect(path: String?): Boolean {
            if (path.isNullOrBlank()) return false
            return try {
                System.load(path)
                true
            } catch (_: Throwable) { false }
        }
        private fun tryLoadFromDir(dir: String?): Boolean {
            if (dir.isNullOrBlank()) return false
            val names = listOf("systray_jna", "systray")
            for (n in names) {
                val file = File(dir, System.mapLibraryName(n))
                if (file.isFile && file.canRead()) {
                    if (tryLoadDirect(file.absolutePath)) return true
                }
            }
            return false
        }
        private fun tryLoadByName(name: String): Boolean = try {
            System.loadLibrary(name)
            true
        } catch (_: Throwable) { false }

        val INSTANCE: GoSystray = run {
            // Try explicit overrides first
            val explicit = System.getProperty("composetray.native.lib")
            val explicitDir = System.getProperty("composetray.native.lib.path")
            var loaded = tryLoadDirect(explicit) || tryLoadFromDir(explicitDir)

            // Then try common names via java.library.path
            if (!loaded) loaded = tryLoadByName("systray_jna") || tryLoadByName("systray")

            // Create proxy via JNA (it won't reload if already loaded)
            // Prefer systray_jna, fallback to systray
            try {
                Native.load("systray_jna", GoSystray::class.java) as GoSystray
            } catch (_: UnsatisfiedLinkError) {
                Native.load("systray", GoSystray::class.java) as GoSystray
            }
        }
    }

    // Callback types -------------------------------------------------------------
    interface VoidCallback : Callback { fun invoke() }
    interface MenuItemCallback : Callback { fun invoke(menuId: Int) }

    // Lifecycle / loop -----------------------------------------------------------
    fun Systray_InitCallbacks(ready: VoidCallback?, exit: VoidCallback?, onClick: VoidCallback?, onRClick: VoidCallback?, onMenuItem: MenuItemCallback?)
    fun Systray_PrepareExternalLoop()
    fun Systray_NativeStart()
    fun Systray_NativeEnd()
    fun Systray_Quit()

    // Tray properties ------------------------------------------------------------
    fun Systray_SetIcon(iconBytes: ByteArray, length: Int)
    fun Systray_SetTitle(title: String?)
    fun Systray_SetTooltip(tooltip: String?)

    // Menu building --------------------------------------------------------------
    fun Systray_ResetMenu()
    fun Systray_AddSeparator()
    fun Systray_AddMenuItem(title: String?, tooltip: String?): Int
    fun Systray_AddMenuItemCheckbox(title: String?, tooltip: String?, checked: Int): Int
    fun Systray_AddSubMenuItem(parentID: Int, title: String?, tooltip: String?): Int
    fun Systray_AddSubMenuItemCheckbox(parentID: Int, title: String?, tooltip: String?, checked: Int): Int

    // Per-item operations --------------------------------------------------------
    fun Systray_MenuItem_SetTitle(id: Int, title: String?): Int
    fun Systray_MenuItem_Enable(id: Int)
    fun Systray_MenuItem_Disable(id: Int)
    fun Systray_MenuItem_Show(id: Int)
    fun Systray_MenuItem_Hide(id: Int)
    fun Systray_MenuItem_Check(id: Int)
    fun Systray_MenuItem_Uncheck(id: Int)

    fun Systray_SetMenuItemIcon(iconBytes: ByteArray, length: Int, id: Int)
}