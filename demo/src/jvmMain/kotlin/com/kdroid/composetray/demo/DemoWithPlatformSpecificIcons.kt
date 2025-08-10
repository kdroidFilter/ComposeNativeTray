package com.kdroid.composetray.demo

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.kdroid.composetray.demo.svg.AcademicCap
import com.kdroid.composetray.tray.api.Tray
import com.kdroid.composetray.utils.ComposeNativeTrayLoggingLevel
import com.kdroid.composetray.utils.SingleInstanceManager
import com.kdroid.composetray.utils.allowComposeNativeTrayLogging
import com.kdroid.composetray.utils.composeNativeTrayloggingLevel
import com.kdroid.composetray.utils.getTrayPosition
import composenativetray.demo.generated.resources.Res
import composenativetray.demo.generated.resources.icon
import composenativetray.demo.generated.resources.icon2
import org.jetbrains.compose.resources.painterResource

/**
 * Demo application that showcases the use of the platform-specific icon API for tray icons.
 * This demo uses:
 * - A Painter resource for Windows
 * - An ImageVector for macOS and Linux
 */
fun main() = application {
    allowComposeNativeTrayLogging = true
    composeNativeTrayloggingLevel = ComposeNativeTrayLoggingLevel.DEBUG

    val logTag = "PlatformSpecificIconsDemo"
    
    println("$logTag: TrayPosition: ${getTrayPosition()}")

    var isWindowVisible by remember { mutableStateOf(true) }
    var alwaysShowTray by remember { mutableStateOf(true) }
    var hideOnClose by remember { mutableStateOf(true) }
    
    // Icon states for switching between different options
    var currentWindowsIcon by remember { mutableStateOf(Res.drawable.icon) }
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
        // Using the platform-specific icon API
        Tray(
            windowsIcon = painterResource(currentWindowsIcon),  // Used on Windows
            macLinuxIcon = AcademicCap,                         // Used on macOS and Linux
            tint = iconTint,                                    // Applied to the ImageVector on macOS/Linux
            tooltip = "Platform-Specific Icons Demo",
            primaryAction = {
                isWindowVisible = true
                println("$logTag: Primary action clicked")
            }
        ) {
            // Menu for Windows icon options (only affects Windows)
            SubMenu(label = "Windows Icon") {
                Item(label = "Icon 1") {
                    currentWindowsIcon = Res.drawable.icon
                    println("$logTag: Switched to Windows icon 1")
                }
                Item(label = "Icon 2") {
                    currentWindowsIcon = Res.drawable.icon2
                    println("$logTag: Switched to Windows icon 2")
                }
            }
            
            // Menu for ImageVector tint options (only affects macOS/Linux)
            SubMenu(label = "macOS/Linux Icon Color") {
                Item(label = "Default (Auto)") {
                    iconTint = null
                    println("$logTag: Icon color set to default (auto)")
                }
                Item(label = "Red") {
                    iconTint = Color.Red
                    println("$logTag: Icon color set to red")
                }
                Item(label = "Green") {
                    iconTint = Color.Green
                    println("$logTag: Icon color set to green")
                }
                Item(label = "Blue") {
                    iconTint = Color.Blue
                    println("$logTag: Icon color set to blue")
                }
                Item(label = "Yellow") {
                    iconTint = Color.Yellow
                    println("$logTag: Icon color set to yellow")
                }
            }

            Divider()

            // Standard menu items
            Item(label = "About") {
                println("$logTag: Platform-Specific Icons Demo - Using different icon types based on platform")
            }

            Divider()

            // Settings for tray visibility
            CheckableItem(
                label = "Always show tray",
                checked = alwaysShowTray,
                onCheckedChange = { checked ->
                    alwaysShowTray = checked
                    println("$logTag: Always show tray ${if (checked) "enabled" else "disabled"}")
                }
            )

            CheckableItem(
                label = "Hide on close",
                checked = hideOnClose,
                onCheckedChange = { checked ->
                    hideOnClose = checked
                    println("$logTag: Hide on close ${if (checked) "enabled" else "disabled"}")
                }
            )

            Divider()

            Item(label = "Hide in tray") {
                isWindowVisible = false
                println("$logTag: Application hidden in tray")
            }

            Item(label = "Exit") {
                println("$logTag: Exiting application")
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
        title = "Platform-Specific Icons Demo",
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