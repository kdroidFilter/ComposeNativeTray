package com.kdroid.composetray.demo

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import co.touchlab.kermit.Logger
import com.kdroid.composetray.demo.svg.AcademicCap
import com.kdroid.composetray.tray.api.Tray
import com.kdroid.composetray.utils.ComposeNativeTrayLoggingLevel
import com.kdroid.composetray.utils.SingleInstanceManager
import com.kdroid.composetray.utils.allowComposeNativeTrayLogging
import com.kdroid.composetray.utils.composeNativeTrayloggingLevel
import com.kdroid.composetray.utils.getTrayPosition
import composenativetray.demo.generated.resources.Res
import composenativetray.demo.generated.resources.icon
import org.jetbrains.compose.resources.painterResource

/**
 * Demo application that showcases the use of the ImageVector API for tray icons.
 * This demo uses the AcademicCap vector as the tray icon.
 */
fun main() = application {
    allowComposeNativeTrayLogging = true
    composeNativeTrayloggingLevel = ComposeNativeTrayLoggingLevel.DEBUG

    val logTag = "ImageVectorTrayDemo"
    val kermit = Logger.withTag(logTag)

    kermit.d { "TrayPosition: ${getTrayPosition()}" }

    var isWindowVisible by remember { mutableStateOf(true) }
    var alwaysShowTray by remember { mutableStateOf(true) }
    var hideOnClose by remember { mutableStateOf(true) }
    
    // Tint color state for the icon
    var iconTint by remember { mutableStateOf<Color?>(null) } // null means use default (white/black based on theme)

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
        // Using the ImageVector API with the AcademicCap vector
        Tray(
            icon = AcademicCap,  // Using the ImageVector directly
            tint = iconTint,     // Using the tint parameter (null means auto-adapt to theme)
            tooltip = "Academic Cap Demo",
            primaryAction = {
                isWindowVisible = true
                kermit.i { "Primary action clicked" }
            },
            primaryActionLabel = "Open Application"
        ) {
            // Menu items to demonstrate changing the tint color
            SubMenu(label = "Icon Color") {
                Item(label = "Default (Auto)") {
                    iconTint = null
                    kermit.i { "Icon color set to default (auto)" }
                }
                Item(label = "Red") {
                    iconTint = Color.Red
                    kermit.i { "Icon color set to red" }
                }
                Item(label = "Green") {
                    iconTint = Color.Green
                    kermit.i { "Icon color set to green" }
                }
                Item(label = "Blue") {
                    iconTint = Color.Blue
                    kermit.i { "Icon color set to blue" }
                }
                Item(label = "Yellow") {
                    iconTint = Color.Yellow
                    kermit.i { "Icon color set to yellow" }
                }
            }

            Divider()

            // Standard menu items
            Item(label = "About") {
                kermit.i { "ImageVector API Demo - Using AcademicCap vector" }
            }

            Divider()

            // Settings for tray visibility
            CheckableItem(
                label = "Always show tray",
                checked = alwaysShowTray,
                onCheckedChange = { checked ->
                    alwaysShowTray = checked
                    kermit.i { "Always show tray ${if (checked) "enabled" else "disabled"}" }
                }
            )

            CheckableItem(
                label = "Hide on close",
                checked = hideOnClose,
                onCheckedChange = { checked ->
                    hideOnClose = checked
                    kermit.i { "Hide on close ${if (checked) "enabled" else "disabled"}" }
                }
            )

            Divider()

            Item(label = "Hide in tray") {
                isWindowVisible = false
                kermit.i { "Application hidden in tray" }
            }

            Item(label = "Exit") {
                kermit.i { "Exiting application" }
                dispose()
                exitApplication()
            }
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
        title = "ImageVector Tray Demo - AcademicCap",
        visible = isWindowVisible,
        icon = painterResource(Res.drawable.icon)
    ) {
        window.toFront()
        App(
            textVisible = true,
            alwaysShowTray = alwaysShowTray,
            hideOnClose = hideOnClose
        ) { alwaysShow, hideOnCloseState ->
            alwaysShowTray = alwaysShow
            hideOnClose = hideOnCloseState
        }
    }
}