package com.kdroid.composetray.demo

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.kdroid.composetray.tray.api.Tray
import com.kdroid.composetray.utils.ComposeNativeTrayLoggingLevel
import com.kdroid.composetray.utils.allowComposeNativeTrayLogging
import com.kdroid.composetray.utils.composeNativeTrayloggingLevel
import composenativetray.demo.generated.resources.Res
import composenativetray.demo.generated.resources.icon
import composenativetray.demo.generated.resources.icon2
import org.jetbrains.compose.resources.painterResource

/**
 * DemoToggleEmptyMenu
 *
 * Minimal sample to test the Linux bug when switching the tray menu
 * from empty to non-empty (and back). Left-click the tray icon to toggle
 * the presence of a single menu item. When the flag is false, the menuContent
 * is intentionally left empty.
 */
fun main() = application {
    // Enable logging to help diagnose behavior on Linux
    allowComposeNativeTrayLogging = false
    composeNativeTrayloggingLevel = ComposeNativeTrayLoggingLevel.DEBUG

    var showMenuItem by remember { mutableStateOf(false) }
    val icon = painterResource(Res.drawable.icon)
    val icon2 = painterResource(Res.drawable.icon2)

    Tray(
        iconContent = {
            Image(
                painter =  icon,
                contentDescription = "ComposeNativeTray Demo",
                modifier = Modifier.fillMaxSize()
            )
        },
        primaryAction = {
            // Toggle between empty and non-empty menu
            showMenuItem = !showMenuItem
           println("Toggled showMenuItem to $showMenuItem")
        },
        tooltip = "Toggle Empty Menu"
    ) {
        // When showMenuItem is false, this block remains empty by design
        if (showMenuItem) {
            Item("I'm here when toggled ON") {
                // Clicking this item turns the menu empty again on next open
                showMenuItem = false
                println("Clicked showMenuItem to $showMenuItem")
            }
        }


    }
    Window( onCloseRequest = ::exitApplication) {
        Text("Compose Native Tray Demo")
    }
}
