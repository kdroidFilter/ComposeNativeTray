package com.kdroid.composetray.demo

import androidx.compose.foundation.Image
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.kdroid.composetray.tray.api.Tray
import com.kdroid.composetray.utils.SingleInstanceManager
import com.kdroid.composetray.utils.getTrayPosition
import com.kdroid.kmplog.Log
import com.kdroid.kmplog.d
import com.kdroid.kmplog.i
import composenativetray.demo.generated.resources.Res
import composenativetray.demo.generated.resources.icon
import composenativetray.demo.generated.resources.icon2
import org.jetbrains.compose.resources.painterResource

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

    val running = serviceStatus == ServiceStatus.RUNNING
    var icon by remember {   mutableStateOf(Res.drawable.icon) }

    // Always create the Tray composable, but make it conditional on visibility
    // This ensures it's recomposed when alwaysShowTray changes
    val showTray = alwaysShowTray || !isWindowVisible

    if (showTray) {
        Tray(
            iconContent = {
                Image(
                    painter = painterResource(icon),
                    contentDescription = "Application Icon",
                    // Use alwaysShowTray as a key to force recomposition when it changes
                )
            },
            primaryAction = {
                isWindowVisible = true
                Log.i(logTag, "On Primary Clicked")
            },
            primaryActionLabel = "Open the Application",
            tooltip = "My Application",
            menuContent = {
                Item("Change icon") {
                    icon = if (icon == Res.drawable.icon) Res.drawable.icon2 else Res.drawable.icon
                }
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
                }

                Divider()

                Item(label = "About") {
                    Log.i(logTag, "Application v1.0 - Developed by Elyahou")
                }

                Divider()

                CheckableItem(label = "Always show tray", checked = alwaysShowTray) { isChecked ->
                    alwaysShowTray = isChecked
                    Log.i(logTag, "Always show tray ${if (isChecked) "enabled" else "disabled"}")
                }

                CheckableItem(label = "Hide on close", checked = hideOnClose) { isChecked ->
                    hideOnClose = isChecked
                    Log.i(logTag, "Hide on close ${if (isChecked) "enabled" else "disabled"}")
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
        icon = painterResource(Res.drawable.icon) // Optional: Set window icon
    ) {
        App(textVisible, alwaysShowTray, hideOnClose) { alwaysShow, hideOnCloseState ->
            alwaysShowTray = alwaysShow
            hideOnClose = hideOnCloseState
        }
    }
}
