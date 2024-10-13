
import com.kdroid.composetray.callbacks.windows.StdCallCallback
import com.sun.jna.Pointer
import com.sun.jna.Structure

@Structure.FieldOrder("text", "disabled", "checked", "cb", "submenu")
internal open class WindowsNativeTrayMenuItem : Structure() {
    @JvmField
    var text: String? = null

    @JvmField
    var disabled: Int = 0

    @JvmField
    var checked: Int = 0

    @JvmField
    var cb: StdCallCallback? = null

    @JvmField
    var submenu: Pointer? = null
}