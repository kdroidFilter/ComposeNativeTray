package com.kdroid.composetray.demo

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

fun main() = application {
    Log.setDevelopmentMode(true)
    val logTag = "NativeTray"

    Log.d("TrayPosition", getTrayPosition().toString())

    var isWindowVisible by remember { mutableStateOf(true) }
    var textVisible by remember { mutableStateOf(false) }
    var alwaysShowTray by remember { mutableStateOf(false) }
    var hideOnClose by remember { mutableStateOf(true) }

    // Dynamic menu state
    var showAdvancedOptions by remember { mutableStateOf(true) }
    var dynamicItemLabel by remember { mutableStateOf("Dynamic Item") }
    var itemCounter by remember { mutableStateOf(0) }

    val isSingleInstance = SingleInstanceManager.isSingleInstance(onRestoreRequest = {
        isWindowVisible = true
    })

    if (!isSingleInstance) {
        exitApplication()
        return@application
    }

    // Always create the Tray composable, but make it conditional on visibility
    val showTray = alwaysShowTray || !isWindowVisible

    if (showTray) {
        Tray(
            iconContent = {
                Icon(
                    Icons.Default.Favorite,
                    contentDescription = "",
                    tint = Color.White,
                    modifier = Modifier.fillMaxSize()
                )
            },
            primaryAction = {
                isWindowVisible = true
                Log.i(logTag, "On Primary Clicked")
            },
            primaryActionLabel = "Open the Application",
            tooltip = "My Application"
            // Note: No menuKey needed anymore!
        ) {
            // Dynamic item that changes label
            Item(label = dynamicItemLabel) {
                itemCounter++
                dynamicItemLabel = "Clicked $itemCounter times"
                Log.i(logTag, "Dynamic item clicked: $dynamicItemLabel")
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

                // Conditionally show advanced options
                if (showAdvancedOptions) {
                    SubMenu(label = "Advanced Sub-options") {
                        Item(label = "Advanced Option 1") {
                            Log.i(logTag, "Advanced Option 1 selected")
                        }
                        Item(label = "Advanced Option 2") {
                            Log.i(logTag, "Advanced Option 2 selected")
                        }
                    }
                }
            }

            Divider()

            // Tools SubMenu
            SubMenu(label = "Tools") {
                Item(label = "Calculator") {
                    Log.i(logTag, "Calculator launched")
                }
                Item(label = "Notepad") {
                    Log.i(logTag, "Notepad opened")
                }
            }

            Divider()

            // Checkable Items
            CheckableItem(label = "Enable notifications") { isChecked ->
                Log.i(logTag, "Notifications ${if (isChecked) "enabled" else "disabled"}")
            }
            CheckableItem(label = "Initial Checked", checked = true) { isChecked ->
                Log.i(logTag, "Initial Checked ${if (isChecked) "enabled" else "disabled"}")
            }

            Divider()

            // Toggle advanced options visibility
            CheckableItem(label = "Show advanced options", checked = showAdvancedOptions) { isChecked ->
                showAdvancedOptions = isChecked
                Log.i(logTag, "Advanced options ${if (isChecked) "shown" else "hidden"}")
            }

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

            Item(label = "Hide in tray") {
                isWindowVisible = false
            }

            Item(label = "Exit", isEnabled = true) {
                Log.i(logTag, "Exiting the application")
                dispose()
                exitApplication()
            }

            Item(label = "Version 1.0.0", isEnabled = false)
        }
    }

    Window(
        onCloseRequest = {
            if (hideOnClose) {
                isWindowVisible = false
            } else {
                exitApplication()
            }
        },
        title = "Compose Desktop Application with Dynamic Tray Menu",
        visible = isWindowVisible,
        icon = org.jetbrains.compose.resources.painterResource(Res.drawable.icon)
    ) {
        App(textVisible, alwaysShowTray, hideOnClose) { alwaysShow, hideOnCloseState ->
            alwaysShowTray = alwaysShow
            hideOnClose = hideOnCloseState
        }
    }
}