
import com.kdroid.composetray.callbacks.windows.MenuItemCallback
import com.sun.jna.Pointer
import com.sun.jna.Structure

// Structure représentant un élément de menu dans le tray
@Structure.FieldOrder("text", "disabled", "checked", "cb", "submenu")
open class WindowsNativeTrayMenuItem : Structure() {
    @JvmField
    var text: String? = null

    @JvmField
    var disabled: Int = 0

    @JvmField
    var checked: Int = 0

    @JvmField
    var cb: MenuItemCallback? = null

    @JvmField
    var submenu: Pointer? = null
}