package com.kdroid.composetray.demo
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import co.touchlab.kermit.Logger
import com.kdroid.composetray.tray.api.Tray
import com.kdroid.composetray.utils.SingleInstanceManager
import com.kdroid.composetray.utils.allowComposeNativeTrayLogging
import com.kdroid.composetray.utils.getTrayPosition
import com.kdroid.composetray.utils.getTrayWindowPosition
import composenativetray.demo.generated.resources.Res
import composenativetray.demo.generated.resources.icon
import org.jetbrains.compose.resources.painterResource

fun main() = application {
    allowComposeNativeTrayLogging = true
    val logTag = "NativeTray"
    val kermit = Logger.withTag(logTag)

    kermit.d { "TrayPosition: ${getTrayPosition()}" }

    var isWindowVisible by remember { mutableStateOf(true) }
    var textVisible by remember { mutableStateOf(false) }
    var alwaysShowTray by remember { mutableStateOf(false) }
    var hideOnClose by remember { mutableStateOf(true) }
    var notificationsEnabled by remember { mutableStateOf(false) }
    var initialChecked by remember { mutableStateOf(true) }

    val isSingleInstance = SingleInstanceManager.isSingleInstance(onRestoreRequest = {
        isWindowVisible = true
    })

    if (!isSingleInstance) {
        exitApplication()
        return@application
    }


    // Always create the Tray composable, but make it conditional on visibility
    // This ensures it's recomposed when alwaysShowTray changes
    val showTray = alwaysShowTray || !isWindowVisible

    if (showTray) {
        // Use alwaysShowTray as a key to force recomposition when it changes
        Tray(
           iconContent = {
               Icon(
                   Icons.Default.Notifications,
                   contentDescription = "Application Icon",
                   modifier = Modifier.fillMaxSize(),
                   tint = Color.White,
               )
           },
            primaryAction = {
                isWindowVisible = true
                kermit.i { "$logTag: On Primary Clicked" }
            },
            primaryActionLabel = "Open the Application",
            tooltip = "My Application"
        ) {

            // Tools SubMenu
            SubMenu(label = "Tools") {
                Item(label = "Calculator") {
                    kermit.i { "$logTag: Calculator launched" }
                }
                Item(label = "Notepad") {
                    kermit.i { "$logTag: Notepad opened" }
                }
            }

            Divider()

            // Checkable Items
            CheckableItem(
                label = "Enable notifications",
                checked = notificationsEnabled,
                onCheckedChange = { isChecked ->
                    notificationsEnabled = isChecked
                    kermit.i { "$logTag: Notifications ${if (isChecked) "enabled" else "disabled"}" }
                }
            )
            CheckableItem(
                label = "Initial Checked",
                checked = initialChecked,
                onCheckedChange = { isChecked ->
                    initialChecked = isChecked
                    kermit.i { "$logTag: Initial Checked ${if (isChecked) "enabled" else "disabled"}" }
                }
            )

            Divider()

            Item(label = "About") {
                kermit.i { "$logTag: Application v1.0 - Developed by Elyahou" }
            }

            Divider()

            CheckableItem(
                label = "Always show tray",
                checked = alwaysShowTray,
                onCheckedChange = { isChecked ->
                    alwaysShowTray = isChecked
                    kermit.i { "$logTag: Always show tray ${if (isChecked) "enabled" else "disabled"}" }
                }
            )

            CheckableItem(
                label = "Hide on close",
                checked = hideOnClose,
                onCheckedChange = { isChecked ->
                    hideOnClose = isChecked
                    kermit.i { "$logTag: Hide on close ${if (isChecked) "enabled" else "disabled"}" }
                }
            )

            Divider()


            Item(label = "Exit", isEnabled = true) {
                kermit.i { "$logTag: Exiting the application" }
                dispose()
                exitApplication()
            }

            Item(label = "Version 1.0.0", isEnabled = false)
        }
    }


    val windowWidth = 800
    val windowHeight = 600
    val windowPosition = getTrayWindowPosition(windowWidth, windowHeight)

    Window(
        onCloseRequest = {
            if (hideOnClose) {
                isWindowVisible = false
            } else {
                exitApplication()
            }
        },
        state = rememberWindowState(
            width = windowWidth.dp,
            height = windowHeight.dp,
            position = windowPosition
        ),
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