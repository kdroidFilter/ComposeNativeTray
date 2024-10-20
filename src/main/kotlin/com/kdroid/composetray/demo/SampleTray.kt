package com.kdroid.composetray.demo

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.ApplicationScope
import com.kdroid.composetray.tray.api.Tray
import com.kdroid.kmplog.Log
import com.kdroid.kmplog.i
import java.nio.file.Paths
import kotlin.system.exitProcess

@Composable
internal fun ApplicationScope.SampleTray() {
    val iconPath = Paths.get("src/test/resources/icon.png").toAbsolutePath().toString()
    val windowsIconPath = Paths.get("src/test/resources/icon.ico").toAbsolutePath().toString()
    val logTag = "NativeTray"
    Tray(
        iconPath = iconPath,
        windowsIconPath = windowsIconPath,
        primaryAction = {
            Log.i(logTag, "On Primary Clicked")
        },
        tooltip = "My Application"
    ) {
        SubMenu(label = "Options") {
            Item(label = "Setting 1") {
                Log.i(logTag, "Setting 1 selected")
            }
            SubMenu(label = "Advanced Sub-options") {
                Item(label = "Advanced Option 1") {
                    Log.i(logTag, "Advanced Option 1 selected")
                }
                Item(label = "Advanced Option 2") {
                    Log.i(logTag, "Advanced Option 2 selected")
                }
            }
        }

        Divider()

        SubMenu(label = "Tools") {
            Item(label = "Calculator") {
                Log.i(logTag, "Calculator launched")
            }
            Item(label = "Notepad") {
                Log.i(logTag, "Notepad opened")
            }
        }

        Divider()

        CheckableItem(label = "Enable notifications") { isChecked ->
            Log.i(logTag, "Notifications ${if (isChecked) "enabled" else "disabled"}")
        }

        Divider()

        Item(label = "About") {
            Log.i(logTag, "Application v1.0 - Developed by Elyahou")
        }

        Divider()

        Item(label = "Exit", isEnabled = true) {
            Log.i(logTag, "Exiting the application")
            dispose()
            exitProcess(0)
        }

        Item(label = "Version 1.0.0", isEnabled = false)
    }
}