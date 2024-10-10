import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.win32.StdCallLibrary

@Structure.FieldOrder("text", "disabled", "checked", "cb", "submenu")
open class WindowsNativeTrayMenuItem : Structure() {
    @JvmField
    var text: Pointer? = null

    @JvmField
    var disabled: Int = 0

    @JvmField
    var checked: Int = 0

    @JvmField
    var cb: MenuItemCallback? = null

    @JvmField
    var submenu: Pointer? = null

    interface MenuItemCallback : StdCallLibrary.StdCallCallback {
        fun invoke(item: WindowsNativeTrayMenuItem)
    }

    class ByReference : WindowsNativeTrayMenuItem(), Structure.ByReference
}