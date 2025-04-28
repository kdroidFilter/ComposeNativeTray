package sample

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.kdroid.composetray.tray.api.Tray
import com.kdroid.composetray.utils.SingleInstanceManager
import com.kdroid.composetray.utils.getTrayPosition
import com.kdroid.kmplog.Log
import com.kdroid.kmplog.d
import com.kdroid.kmplog.i

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
    var iconColor by remember { mutableStateOf(Color.White) }

    val running = serviceStatus == ServiceStatus.RUNNING

    if (alwaysShowTray || !isWindowVisible) {
        Tray(
            iconContent = {
                Box(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(300.dp)).background(iconColor))
            },
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
                       iconColor = if (iconColor == Color.White) Color.Red else Color.White
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
