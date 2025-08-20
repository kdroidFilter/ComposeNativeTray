package com.kdroid.composetray.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment.Companion.Center
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.kdroid.composetray.lib.mac.MacOSWindowManager
import com.kdroid.composetray.tray.api.TrayApp

fun main() {

    application {
        var isWindowVisible by remember { mutableStateOf(true) }
        var shouldRestoreWindow by remember { mutableStateOf(false) }
        TrayApp(
            icon = Icons.Default.Book,
            tooltip = "TrayAppDemo",
            windowSize = DpSize(300.dp, 500.dp),
            transparent = true,
            visibleOnStart = false,
            content = {
                MaterialTheme {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background),
                        contentAlignment = Center,
                    ) {
                        Text("Hello World !")
                    }
                }
            },
            menu = {
                Item("Open the app", onClick = {
                    if (!isWindowVisible) {
                        isWindowVisible = true
                    } else {
                        shouldRestoreWindow = true
                    }
                })
                Item("Exit", onClick = { exitApplication() })
            }
        )

        if (isWindowVisible) {
            MacOSWindowManager().showInDock()
            val state = rememberWindowState()

            Window(
                state = state,
                onCloseRequest = {
                    isWindowVisible = false
                }) {

                LaunchedEffect(shouldRestoreWindow) {
                    if (shouldRestoreWindow) {
                        state.isMinimized = false
                        window.toFront()
                        window.requestFocusInWindow()
                        window.requestFocus()
                        shouldRestoreWindow = false 
                    }
                }
                Text("Compose Native Tray Demo")
            }
        } else {
            MacOSWindowManager().hideFromDock()
        }
    }
}
