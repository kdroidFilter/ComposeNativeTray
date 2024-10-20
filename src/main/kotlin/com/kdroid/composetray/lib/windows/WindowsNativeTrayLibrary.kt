import com.sun.jna.Pointer
import com.sun.jna.win32.StdCallLibrary

internal interface WindowsNativeTrayLibrary : StdCallLibrary {
    fun tray_get_instance(): Pointer?
    fun tray_init(tray: WindowsNativeTray): Int
    fun tray_loop(blocking: Int): Int
    fun tray_update(tray: WindowsNativeTray)
    fun tray_exit()
    fun tray_free_menu(menu: Pointer)
}
