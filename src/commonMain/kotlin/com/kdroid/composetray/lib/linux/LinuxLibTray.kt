package com.kdroid.composetray.lib.linux

import com.sun.jna.Callback
import com.sun.jna.Native
import com.sun.jna.ptr.IntByReference
import java.io.File

/**
 * JNA direct mapping to the Go-based systray bridge (linuxlibnew/jna/bridge_linux.go).
 *
 * We try to register a shared library named "systray_jna" first, then fallback to "systray".
 * You can override via system properties:
 * -Dcomposetray.native.lib=/abs/path/to/libsystray_jna.so
 * -Dcomposetray.native.lib.path=/abs/dir/containing/lib
 */
internal object LinuxLibTray {
    // Callback types -------------------------------------------------------------
    interface VoidCallback : Callback { fun invoke() }
    interface MenuItemCallback : Callback { fun invoke(menuId: Int) }

    // Library registration logic -------------------------------------------------
    private fun existingFile(path: String?): String? =
        path?.takeIf { it.isNotBlank() }?.let { p -> File(p).takeIf { it.isFile && it.canRead() }?.absolutePath }

    private fun findInDir(dir: String?): List<String> {
        if (dir.isNullOrBlank()) return emptyList()
        val d = File(dir)
        if (!d.isDirectory || !d.canRead()) return emptyList()
        val names = listOf("systray_jna", "systray")
        return names.map { n -> File(d, System.mapLibraryName(n)) }
            .filter { it.isFile && it.canRead() }
            .map { it.absolutePath }
    }

    private fun tryRegister(target: String): Boolean = try {
        Native.register(target)
        true
    } catch (_: Throwable) { false }

    init {
        // Try explicit overrides first
        val explicitPath = existingFile(System.getProperty("composetray.native.lib"))
        val fromDir = findInDir(System.getProperty("composetray.native.lib.path"))

        val candidates = buildList {
            if (explicitPath != null) add(explicitPath)
            addAll(fromDir)
            add("systray_jna")
            add("systray")
        }

        var registered = false
        for (c in candidates) {
            if (tryRegister(c)) { registered = true; break }
        }
        if (!registered) {
            // Last chance: if explicit path was provided but not an existing file, try to load it then register names
            val loaded = try {
                val p = System.getProperty("composetray.native.lib")
                if (!p.isNullOrBlank()) { System.load(p); true } else false
            } catch (_: Throwable) { false }
            if (loaded) {
                registered = tryRegister("systray_jna") || tryRegister("systray")
            }
        }
        if (!registered) throw UnsatisfiedLinkError("Failed to register Linux systray native library (tried: ${candidates.joinToString()})")
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