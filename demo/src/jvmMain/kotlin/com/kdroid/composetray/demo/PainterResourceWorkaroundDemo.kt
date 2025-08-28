package com.kdroid.composetray.demo

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.kdroid.composetray.tray.api.Tray
import com.kdroid.composetray.utils.ComposeNativeTrayLoggingLevel
import com.kdroid.composetray.utils.SingleInstanceManager
import com.kdroid.composetray.utils.allowComposeNativeTrayLogging
import com.kdroid.composetray.utils.composeNativeTrayLoggingLevel
import composenativetray.demo.generated.resources.Res
import composenativetray.demo.generated.resources.icon
import composenativetray.demo.generated.resources.icon2
import org.jetbrains.compose.resources.painterResource

/**
 * Demo application that showcases the workaround for using painterResource with menu items.
 * This demo demonstrates how to properly use painterResource with menu items by declaring
 * the icon variables in the composable context.
 */
fun main() = application {
    allowComposeNativeTrayLogging = true
    composeNativeTrayLoggingLevel = ComposeNativeTrayLoggingLevel.DEBUG

    val logTag = "PainterResourceWorkaroundDemo"
    
    var isWindowVisible by remember { mutableStateOf(true) }
    var alwaysShowTray by remember { mutableStateOf(true) }
    var hideOnClose by remember { mutableStateOf(true) }
    
    // Declare icon variables in the composable context
    val mainIcon = painterResource(Res.drawable.icon)
    val settingsIcon = painterResource(Res.drawable.icon2)
    
    val isSingleInstance = SingleInstanceManager.isSingleInstance(onRestoreRequest = {
        isWindowVisible = true
    })

    if (!isSingleInstance) {
        exitApplication()
        return@application
    }

    val showTray = alwaysShowTray || !isWindowVisible

    if (showTray) {
        Tray(
            icon = mainIcon,  // Using the cached painter
            tooltip = "Painter Resource Workaround Demo",
            primaryAction = {
                isWindowVisible = true
                println("$logTag: Primary action clicked")
            }
        ) {
            // Using the cached painter in a menu item
            SubMenu(
                label = "Settings",
                icon = settingsIcon  // Works correctly because we're using a variable
            ) {
                Item(label = "Option 1") {
                    println("$logTag: Option 1 selected")
                }
                
                Item(label = "Option 2") {
                    println("$logTag: Option 2 selected")
                }
            }
            
            Divider()
            
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
        title = "Painter Resource Workaround Demo",
        visible = isWindowVisible,
        icon = mainIcon  // Using the cached painter for the window icon
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