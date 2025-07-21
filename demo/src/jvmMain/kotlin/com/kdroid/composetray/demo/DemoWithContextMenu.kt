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
import co.touchlab.kermit.Logger
import com.kdroid.composetray.tray.api.Tray
import com.kdroid.composetray.utils.SingleInstanceManager
import com.kdroid.composetray.utils.allowComposeNativeTrayLogging
import com.kdroid.composetray.utils.getTrayPosition
import com.kdroid.composetray.utils.isMenuBarInDarkMode
import composenativetray.demo.generated.resources.Res
import composenativetray.demo.generated.resources.icon

fun main() = application {
    allowComposeNativeTrayLogging = true
    val logTag = "NativeTray"
    val kermit = Logger.withTag("NativeTray")

    kermit.d { "TrayPosition: ${getTrayPosition()}" }

    var isWindowVisible by remember { mutableStateOf(true) }
    var textVisible by remember { mutableStateOf(false) }
    var alwaysShowTray by remember { mutableStateOf(false) }
    var hideOnClose by remember { mutableStateOf(true) }

    // Dynamic menu state
    var showAdvancedOptions by remember { mutableStateOf(true) }
    var dynamicItemLabel by remember { mutableStateOf("Dynamic Item") }
    var itemCounter by remember { mutableStateOf(0) }

    // New idiomatic state management
    var notificationsEnabled by remember { mutableStateOf(false) }
    var darkModeEnabled by remember { mutableStateOf(false) }
    var autoStartEnabled by remember { mutableStateOf(true) }

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
                    tint = if (isMenuBarInDarkMode()) Color.White else Color.Black,
                    modifier = Modifier.fillMaxSize()
                )
            },
            primaryAction = {
                isWindowVisible = true
                kermit.i { "$logTag: On Primary Clicked" }
            },
            primaryActionLabel = "Open the Application",
            tooltip = "My Application"
            // Note: No menuKey needed anymore!
        ) {
            // Dynamic item that changes label
            Item(label = dynamicItemLabel) {
                itemCounter++
                dynamicItemLabel = "Clicked $itemCounter times"
                kermit.i { "$logTag: Dynamic item clicked: $dynamicItemLabel" }
            }

            Divider()

            // Options SubMenu
            SubMenu(label = "Options") {
                Item(label = "Show Text") {
                    kermit.i { "$logTag: Show Text selected" }
                    textVisible = true
                }
                Item(label = "Hide Text") {
                    kermit.i { "$logTag: Hide Text selected" }
                    textVisible = false
                }

                // Conditionally show advanced options
                if (showAdvancedOptions) {
                    SubMenu(label = "Advanced Sub-options") {
                        Item(label = "Advanced Option 1") {
                            kermit.i { "$logTag: Advanced Option 1 selected" }
                        }
                        Item(label = "Advanced Option 2") {
                            kermit.i { "$logTag: Advanced Option 2 selected" }
                        }
                    }
                }
            }

            Divider()

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

            // New idiomatic CheckableItem usage
            CheckableItem(
                label = "Enable notifications",
                checked = notificationsEnabled,
                onCheckedChange = { checked ->
                    notificationsEnabled = checked
                    kermit.i { "$logTag: Notifications ${if (checked) "enabled" else "disabled"}" }
                }
            )

            CheckableItem(
                label = "Dark mode",
                checked = darkModeEnabled,
                onCheckedChange = { checked ->
                    darkModeEnabled = checked
                    kermit.i { "$logTag: Dark mode ${if (checked) "enabled" else "disabled"}" }
                }
            )

            CheckableItem(
                label = "Auto-start on login",
                checked = autoStartEnabled,
                onCheckedChange = { checked ->
                    autoStartEnabled = checked
                    kermit.i { "$logTag: Auto-start ${if (checked) "enabled" else "disabled"}" }
                }
            )

            Divider()

            // Toggle advanced options visibility
            CheckableItem(
                label = "Show advanced options",
                checked = showAdvancedOptions,
                onCheckedChange = { checked ->
                    showAdvancedOptions = checked
                    kermit.i { "$logTag: Advanced options ${if (checked) "shown" else "hidden"}" }
                }
            )

            Item(label = "About") {
                kermit.i { "$logTag: Application v1.0 - Developed by Elyahou" }
            }

            Divider()

            CheckableItem(
                label = "Always show tray",
                checked = alwaysShowTray,
                onCheckedChange = { checked ->
                    alwaysShowTray = checked
                    kermit.i { "$logTag: Always show tray ${if (checked) "enabled" else "disabled"}" }
                }
            )

            CheckableItem(
                label = "Hide on close",
                checked = hideOnClose,
                onCheckedChange = { checked ->
                    hideOnClose = checked
                    kermit.i { "$logTag: Hide on close ${if (checked) "enabled" else "disabled"}" }
                }
            )

            Divider()

            Item(label = "Hide in tray") {
                isWindowVisible = false
            }

            Item(label = "Exit", isEnabled = true) {
                kermit.i { "$logTag: Exiting the application" }
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