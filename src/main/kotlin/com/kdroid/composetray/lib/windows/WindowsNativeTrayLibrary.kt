import com.sun.jna.Pointer
import com.sun.jna.win32.StdCallLibrary

interface WindowsNativeTrayLibrary : StdCallLibrary {
    fun tray_get_instance(): Pointer?
    fun tray_init(tray: WindowsNativeTray): Int
    fun tray_loop(blocking: Int): Int
    fun tray_update(tray: WindowsNativeTray)
    fun tray_exit()
}

