package com.kdroid.composetray.demo

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.kdroid.composetray.tray.api.Tray
import com.kdroid.composetray.utils.SingleInstanceWithCallbackChecker
import com.kdroid.kmplog.Log
import com.kdroid.kmplog.i
import java.nio.file.Paths

fun main() = application {
    Log.setDevelopmentMode(true)
    val iconPath = Paths.get("src/test/resources/icon.png").toAbsolutePath().toString()
    val windowsIconPath = Paths.get("src/test/resources/icon.ico").toAbsolutePath().toString()
    val logTag = "NativeTray"
    var textVisible by remember{ mutableStateOf(false)}

    var isWindowVisible by remember { mutableStateOf(true) }

    // Vérifiez l'instance unique avec callback de restauration
    val isFirstInstance = SingleInstanceWithCallbackChecker.isSingleInstance {
        isWindowVisible = true
    }

    // Si une autre instance est déjà en cours, quitter l'application
    if (!isFirstInstance) {
        exitApplication()
        return@application
    }


    Tray(
        iconPath = iconPath,
        windowsIconPath = windowsIconPath,
        primaryAction = {
            isWindowVisible = true
            Log.i(logTag, "On Primary Clicked")
        },
        tooltip = "My Application"
    ) {
        SubMenu(label = "Options") {
            Item(label = "Show Text") {
                Log.i(logTag, "Show Text selected")
                textVisible = true
            }
            Item(label = "Hide Text") {
                Log.i(logTag, "Hide Text selected")
                textVisible = false
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

    Window(
        onCloseRequest = {isWindowVisible = false},
        title = "Compose Desktop Application with Two Screens",
        visible = isWindowVisible
    ) {
        App(textVisible)
    }
}

@Composable
@Preview
fun App(textVisible: Boolean) {
    var currentScreen by remember { mutableStateOf(Screen.Screen1) }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    when (currentScreen) {
                        Screen.Screen1 -> ScreenOne(onNavigate = { currentScreen = Screen.Screen2 }, textVisible = textVisible)
                        Screen.Screen2 -> ScreenTwo(onNavigate = { currentScreen = Screen.Screen1 }, textVisible = textVisible)
                    }
                }
            }
        }
    }
}

@Composable
fun ScreenOne(onNavigate: () -> Unit, textVisible: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, modifier = Modifier.fillMaxSize()) {
        Text("Screen 1")
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onNavigate) {
            Text("Go to Screen 2")
        }
        Spacer(modifier = Modifier.height(16.dp))
        if (textVisible) {
            Text("This is the additional text displayed based on tray selection.")
        }
    }
}

@Composable
fun ScreenTwo(onNavigate: () -> Unit, textVisible: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, modifier = Modifier.fillMaxSize()) {
        Text("Screen 2")
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onNavigate) {
            Text("Go back to Screen 1")
        }
        Spacer(modifier = Modifier.height(16.dp))
        if (textVisible) {
            Text("This is the additional text displayed based on tray selection.")
        }
    }
}

enum class Screen {
    Screen1,
    Screen2
}
