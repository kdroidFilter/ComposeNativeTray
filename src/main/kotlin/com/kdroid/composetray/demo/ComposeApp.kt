package com.kdroid.composetray.demo

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.Column
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    SampleTray()
    Window(onCloseRequest = ::exitApplication, title = "Application Compose Desktop à Deux Écrans") {
        App()
    }
}

@Composable
@Preview
fun App() {
    var currentScreen by remember { mutableStateOf(Screen.Screen1) }

    MaterialTheme {
        Surface {
            when (currentScreen) {
                Screen.Screen1 -> ScreenOne(onNavigate = { currentScreen = Screen.Screen2 })
                Screen.Screen2 -> ScreenTwo(onNavigate = { currentScreen = Screen.Screen1 })
            }
        }
    }
}

@Composable
fun ScreenOne(onNavigate: () -> Unit) {
    Column {
        Text("Écran 1")
        Button(onClick = onNavigate) {
            Text("Aller à l'écran 2")
        }
    }
}

@Composable
fun ScreenTwo(onNavigate: () -> Unit) {
    Column {
        Text("Écran 2")
        Button(onClick = onNavigate) {
            Text("Revenir à l'écran 1")
        }
    }
}

enum class Screen {
    Screen1,
    Screen2
}
