package com.kdroid.composetray.demo

import androidx.compose.foundation.Image
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import co.touchlab.kermit.Logger
import com.kdroid.composetray.tray.api.Tray
import com.kdroid.composetray.utils.SingleInstanceManager
import com.kdroid.composetray.utils.getTrayPosition
import composenativetray.demo.generated.resources.Res
import composenativetray.demo.generated.resources.icon
import composenativetray.demo.generated.resources.icon2
import org.jetbrains.compose.resources.painterResource
import java.awt.Color
import javax.swing.JFrame
import javax.swing.SwingUtilities
import javax.swing.Timer

private enum class ServiceStatus {
    RUNNING, STOPPED
}

fun main() = application {
    val logTag = "NativeTray"
    val kermit = Logger.withTag(logTag)

    kermit.d { "TrayPosition: ${getTrayPosition()}" }

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
    var icon by remember { mutableStateOf(Res.drawable.icon) }

    // Always create the Tray composable, but make it conditional on visibility
    // This ensures it's recomposed when alwaysShowTray changes
    val showTray = alwaysShowTray || !isWindowVisible
    var isVisible by remember { mutableStateOf(true) }
    var name by remember { mutableStateOf("Change Item Name") }

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
                kermit.i { "On Primary Clicked" }
            },
            primaryActionLabel = "Open the Application",
            tooltip = "My Application",
            // Pass isVisible and name as menuKey to force recomposition when they change
            menuContent = {
                Item("Change icon") {
                    icon = if (icon == Res.drawable.icon) Res.drawable.icon2 else Res.drawable.icon
                }
                if (isVisible) {
                    Item("Hide Me") {
                        isVisible = false
                    }
                }
                Item(name) {
                    name = "I've changed !"
                }
                // Dynamic Service Menu
                SubMenu(label = "Service Control") {
                    Item(label = "Start Service", isEnabled = !running) {
                        kermit.i { "Start Service selected" }
                        serviceStatus = ServiceStatus.RUNNING
                    }
                    Item(label = "Stop Service", isEnabled = running) {
                        kermit.i { "Stop Service selected" }
                        serviceStatus = ServiceStatus.STOPPED
                    }
                    Item(label = "Service Status: ${if (running) "Running" else "Stopped"}", isEnabled = false)
                }

                Divider()

                // Options SubMenu
                SubMenu(label = "Options") {
                    Item(label = "Show Text") {
                        kermit.i { "Show Text selected" }
                        textVisible = true
                    }
                    Item(label = "Hide Text") {
                        kermit.i { "Hide Text selected" }
                        textVisible = false
                    }
                }

                Divider()

                Item(label = "About") {
                    kermit.i { "Application v1.0 - Developed by Elyahou" }
                }

                Divider()

                CheckableItem(
                    label = "Always show tray",
                    checked = alwaysShowTray,
                    onCheckedChange = { isChecked ->
                        alwaysShowTray = isChecked
                        kermit.i { "Always show tray ${if (isChecked) "enabled" else "disabled"}" }
                    }
                )

                CheckableItem(
                    label = "Hide on close",
                    checked = hideOnClose,
                    onCheckedChange = { isChecked ->
                        hideOnClose = isChecked
                        kermit.i { "Hide on close ${if (isChecked) "enabled" else "disabled"}" }
                    }
                )

                Divider()

                Item(label = "Exit", isEnabled = true) {
                    kermit.i { "Exiting the application" }
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
        window.toFront()
        App(textVisible, alwaysShowTray, hideOnClose) { alwaysShow, hideOnCloseState ->
            alwaysShowTray = alwaysShow
            hideOnClose = hideOnCloseState
        }
    }
}