import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.win32.StdCallLibrary


// Structure représentant le tray Windows
@Structure.FieldOrder("icon_filepath", "tooltip", "cb", "menu")
class WindowsNativeTray : Structure() {
    @JvmField
    var icon_filepath: String? = null

    @JvmField
    var tooltip: String? = null

    @JvmField
    var cb: TrayCallback? = null

    @JvmField
    var menu: Pointer? = null

    interface TrayCallback : StdCallLibrary.StdCallCallback {
        fun invoke(tray: WindowsNativeTray)
    }
}