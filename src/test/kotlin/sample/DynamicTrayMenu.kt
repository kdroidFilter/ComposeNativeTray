package sample

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.kdroid.composetray.tray.api.Tray
import com.kdroid.composetray.utils.SingleInstanceManager
import com.kdroid.composetray.utils.getTrayPosition
import com.kdroid.kmplog.Log
import com.kdroid.kmplog.d
import com.kdroid.kmplog.i
import java.nio.file.Paths

private enum class ServiceStatus {
    RUNNING, STOPPED
}

fun main() = application {
    Log.setDevelopmentMode(true)
    val logTag = "NativeTray"

    Log.d("TrayPosition", getTrayPosition().toString())

    var isWindowVisible by remember { mutableStateOf(true) }
    var textVisible by remember { mutableStateOf(false) }
    var alwaysShowTray by remember { mutableStateOf(true) }
    var hideOnClose by remember { mutableStateOf(true) }
    var serviceStatus by remember { mutableStateOf(ServiceStatus.STOPPED) }

    val isSingleInstance = SingleInstanceManager.isSingleInstance(onRestoreRequest = {
        isWindowVisible = true
    })

    if (!isSingleInstance) {
        exitApplication()
        return@application
    }

    // Tray Icon Paths
    var iconPath by remember { mutableStateOf( Paths.get("src/test/resources/icon.png").toAbsolutePath().toString())}
    val windowsIconPath = Paths.get("src/test/resources/icon.ico").toAbsolutePath().toString()

    val running = serviceStatus == ServiceStatus.RUNNING

    if (alwaysShowTray || !isWindowVisible) {
        Tray(
            iconPath = iconPath,
            windowsIconPath = windowsIconPath,
            primaryAction = {
                isWindowVisible = true
                Log.i(logTag, "On Primary Clicked")
            },
            primaryActionLinuxLabel = "Open the Application",
            tooltip = "My Application",
            menuContent = {
                // Dynamic Service Menu
                SubMenu(label = "Service Control") {
                    Item(label = "Start Service", isEnabled = !running) {
                        Log.i(logTag, "Start Service selected")
                        serviceStatus = ServiceStatus.RUNNING
                    }
                    Item(label = "Stop Service", isEnabled = running) {
                        Log.i(logTag, "Stop Service selected")
                        serviceStatus = ServiceStatus.STOPPED
                    }
                    Item(label = "Service Status: ${if (running) "Running" else "Stopped"}", isEnabled = false)
                }

                Divider()

                // Options SubMenu
                SubMenu(label = "Options") {
                    Item(label = "Show Text") {
                        Log.i(logTag, "Show Text selected")
                        textVisible = true
                    }
                    Item(label = "Hide Text") {
                        Log.i(logTag, "Hide Text selected")
                        textVisible = false
                    }
                    Item("Change icon") {
                        if (iconPath == Paths.get("src/test/resources/icon.png").toAbsolutePath().toString())
                        iconPath = Paths.get("src/test/resources/icon2.png").toAbsolutePath().toString()
                        else iconPath = Paths.get("src/test/resources/icon.png").toAbsolutePath().toString()
                    }
                }

                Divider()

                Item(label = "About") {
                    Log.i(logTag, "Application v1.0 - Developed by Elyahou")
                }

                Divider()

                Item(label = "Exit", isEnabled = true) {
                    Log.i(logTag, "Exiting the application")
                    dispose()
                    exitApplication()
                }
            }
        )
    }

    Window(
        onCloseRequest = {
            if (hideOnClose) {
                isWindowVisible = false
            } else {
                exitApplication()
            }
        },
        title = "Compose Desktop Application with Two Screens",
        visible = isWindowVisible,
        icon = painterResource("icon.png") // Optional: Set window icon
    ) {
        App(textVisible, alwaysShowTray, hideOnClose) { alwaysShow, hideOnCloseState ->
            alwaysShowTray = alwaysShow
            hideOnClose = hideOnCloseState
        }
    }
}


