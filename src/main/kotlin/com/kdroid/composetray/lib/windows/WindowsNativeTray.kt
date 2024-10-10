import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.win32.StdCallLibrary

@Structure.FieldOrder("icon_filepath", "tooltip", "cb", "menu")
class WindowsNativeTray : Structure() {
    @JvmField
    var icon_filepath: Pointer? = null

    @JvmField
    var tooltip: Pointer? = null

    @JvmField
    var cb: TrayCallback? = null

    @JvmField
    var menu: Pointer? = null

    interface TrayCallback : StdCallLibrary.StdCallCallback {
        fun invoke(tray: WindowsNativeTray)
    }
}