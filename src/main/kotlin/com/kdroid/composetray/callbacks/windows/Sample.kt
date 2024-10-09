import com.sun.jna.Native
import com.sun.jna.Structure
import com.sun.jna.platform.win32.*
import com.sun.jna.platform.win32.WinDef.*
import com.sun.jna.win32.StdCallLibrary
import javax.swing.JOptionPane
import javax.swing.SwingUtilities

// Define constants at the top level
const val IMAGE_ICON = 1          // From WinUser.h
const val LR_LOADFROMFILE = 0x10  // From WinUser.h
const val IDI_APPLICATION = 32512 // From WinUser.h

// Extend User32 interface at the top level
interface ExtendedUser32 : User32 {
    override fun LoadImage(
        hinst: HINSTANCE?,
        lpszName: String?,
        uType: Int,
        cxDesired: Int,
        cyDesired: Int,
        fuLoad: Int
    ): WinNT.HANDLE?

    override fun LoadIcon(hInstance: HINSTANCE?, lpIconName: String?): HICON?
}

// Extend Shell32 interface at the top level
interface ExtendedShell32 : Shell32 {
    fun Shell_NotifyIcon(dwMessage: Int, lpData: NOTIFYICONDATA): BOOL
}

fun main() {
    // Initialize COM library
    Ole32.INSTANCE.CoInitializeEx(null, Ole32.COINIT_MULTITHREADED)

    val hInstance = Kernel32.INSTANCE.GetModuleHandle(null)
    val callbackMessage = WinAPI.WM_USER + 1

    val wndProc = object : WndProc {
        override fun callback(hWnd: HWND?, uMsg: UINT, wParam: WPARAM, lParam: LPARAM): LRESULT {
            val uMsgInt = uMsg.toInt()
            when (uMsgInt) {
                WinAPI.WM_DESTROY -> {
                    User32.INSTANCE.PostQuitMessage(0)
                    return LRESULT(0)
                }

                callbackMessage -> {
                    val lParamInt = lParam.toInt()
                    when (lParamInt) {
                        WinAPI.WM_LBUTTONUP -> {
                            println("Tray icon clicked")
                        }

                        WinAPI.WM_RBUTTONUP -> {
                            println("Right menu clicked")

                        }
                    }
                    return LRESULT(0)
                }

                else -> return User32.INSTANCE.DefWindowProc(hWnd, uMsgInt, wParam, lParam)
            }
        }
    }

    val wndClass = WinUser.WNDCLASSEX().apply {
        this.hInstance = hInstance
        this.lpfnWndProc = wndProc
        this.lpszClassName = "MyTrayIconClass"
    }

    val atom = User32.INSTANCE.RegisterClassEx(wndClass)
    if (atom.toInt() == 0) {
        println("Failed to register window class")
        return
    }

    val hWnd = User32.INSTANCE.CreateWindowEx(
        0,
        wndClass.lpszClassName,
        "My Tray Icon Window",
        0,
        0,
        0,
        0,
        0,
        null,
        null,
        hInstance,
        null
    )

    if (hWnd == null) {
        println("Failed to create window")
        return
    }

    val hIcon = User32.INSTANCE.LoadImage(
        null,
        "C:\\Users\\Eyahou Gambache\\CLionProjects\\tray\\icon2-24px.png",
        IMAGE_ICON,
        16,
        16,
        LR_LOADFROMFILE
    )

    val iconHandle = hIcon as? HICON ?: User32.INSTANCE.LoadIcon(null, IDI_APPLICATION.toString())

    // Set up NOTIFYICONDATA
    val nid = NOTIFYICONDATA().apply {
        this.hWnd = hWnd
        this.uID = UINT(1)
        this.uFlags = UINT((WinAPI.NIF_MESSAGE or WinAPI.NIF_ICON or WinAPI.NIF_TIP).toLong())
        this.uCallbackMessage = UINT(callbackMessage.toLong())
        this.hIcon = iconHandle
        val tooltip = "My Kotlin Tray Icon"
        System.arraycopy(tooltip.toCharArray(), 0, this.szTip, 0, tooltip.length)
    }

    val Shell32Ex = Native.load("shell32", ExtendedShell32::class.java)

    // Add icon to system tray
    Shell32Ex.Shell_NotifyIcon(WinAPI.NIM_ADD, nid)

    // Message loop
    val msg = WinUser.MSG()
    while (User32.INSTANCE.GetMessage(msg, null, 0, 0) != 0) {
        User32.INSTANCE.TranslateMessage(msg)
        User32.INSTANCE.DispatchMessage(msg)
    }

    // Clean up
    Shell32Ex.Shell_NotifyIcon(WinAPI.NIM_DELETE, nid)
    User32.INSTANCE.DestroyIcon(iconHandle)
    User32.INSTANCE.DestroyWindow(hWnd)
    User32.INSTANCE.UnregisterClass(wndClass.lpszClassName, hInstance)

    Ole32.INSTANCE.CoUninitialize()
}



object WinAPI {
    const val WM_DESTROY = 0x0002
    const val WM_USER = 0x0400
    const val NIF_MESSAGE = 0x00000001
    const val NIF_ICON = 0x00000002
    const val NIF_TIP = 0x00000004
    const val NIM_ADD = 0x00000000
    const val NIM_DELETE = 0x00000002
    const val WM_LBUTTONUP = 0x0202
    const val WM_RBUTTONUP = 0x0205
}

class NOTIFYICONDATA : Structure() {
    @JvmField
    var hWnd: HWND? = null
    @JvmField
    var uID: UINT = UINT(0)
    @JvmField
    var uFlags: UINT = UINT(0)
    @JvmField
    var uCallbackMessage: UINT = UINT(0)
    @JvmField
    var hIcon: HICON? = null
    @JvmField
    var szTip = CharArray(128)
    @JvmField
    var cbSize: DWORD = DWORD(size().toLong())

    override fun getFieldOrder() = listOf(
        "cbSize", "hWnd", "uID", "uFlags", "uCallbackMessage", "hIcon", "szTip"
    )
}

interface WndProc : StdCallLibrary.StdCallCallback {
    fun callback(hWnd: HWND?, uMsg: UINT, wParam: WPARAM, lParam: LPARAM): LRESULT
}
