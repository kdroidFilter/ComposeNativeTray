package com.kdroid.composetray.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Window
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment.Companion.Center
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.kdroid.composetray.tray.api.ExperimentalTrayAppApi
import com.kdroid.composetray.tray.api.Tray
import com.kdroid.composetray.tray.api.TrayApp
import com.kdroid.composetray.utils.WindowRaise
import composenativetray.demo.generated.resources.Res
import composenativetray.demo.generated.resources.icon
import io.github.kdroidfilter.platformtools.darkmodedetector.isSystemInDarkMode
import io.github.kdroidfilter.platformtools.darkmodedetector.mac.setMacOsAdaptiveTitleBar
import io.github.kdroidfilter.platformtools.darkmodedetector.windows.setWindowsAdaptiveTitleBar
import org.jetbrains.compose.resources.painterResource

@OptIn(ExperimentalTrayAppApi::class)
fun main() {
    setMacOsAdaptiveTitleBar()
    application {
        var isWindowVisible by remember { mutableStateOf(true) }
        var textFieldValue by remember { mutableStateOf("") }
        var textFieldValue2 by remember { mutableStateOf("") }

        TrayApp(
            icon = Icons.Default.Book,
            tooltip = "TrayAppDemo",
            windowSize = DpSize(300.dp, 500.dp),
            visibleOnStart = true,
            menu = {
                Item(
                    if (isWindowVisible) "Hide the app" else "Open the App",
                    onClick = {
                        isWindowVisible = !isWindowVisible
                    }
                )
                Item("Exit", onClick = { exitApplication() })
            }
        ) {
            MaterialTheme(
                colorScheme = if (isSystemInDarkMode()) darkColorScheme() else lightColorScheme()
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                        .padding(16.dp),
                    contentAlignment = Center,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Your futur awesome compagnon App !", color = MaterialTheme.colorScheme.onBackground)
                        TextField(
                            value = textFieldValue,
                            onValueChange = { textFieldValue = it },
                            placeholder = { Text("Enter some text") }
                        )
                    }
                }
            }
        }

        TrayApp(
            icon = Icons.Default.Window,
            tooltip = "TrayAppDemo",
            windowSize = DpSize(300.dp, 500.dp),
            visibleOnStart = true,
            menu = {
                Item(
                    if (isWindowVisible) "Hide the app" else "Open the App",
                    onClick = {
                        isWindowVisible = !isWindowVisible
                    }
                )
                Item("Exit", onClick = { exitApplication() })
            }
        ) {
            MaterialTheme(
                colorScheme = if (isSystemInDarkMode()) darkColorScheme() else lightColorScheme()
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                        .padding(16.dp),
                    contentAlignment = Center,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Your futur awesome compagnon App !", color = MaterialTheme.colorScheme.onBackground)
                        TextField(
                            value = textFieldValue2,
                            onValueChange = { textFieldValue2 = it },
                            placeholder = { Text("Enter some text") }
                        )
                    }
                }
            }
        }


        if (isWindowVisible) {
            val state = rememberWindowState()

            Window(
                state = state,
                onCloseRequest = { isWindowVisible = false },
                title = "Main App",
                icon = painterResource(Res.drawable.icon),
            ) {
                // Use Windows adaptive title bar when available
                window.setWindowsAdaptiveTitleBar()

                MaterialTheme(
                    colorScheme = if (isSystemInDarkMode()) darkColorScheme() else lightColorScheme()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background),
                        contentAlignment = Center,
                    ) {
                        Column {
                            Text("Your futur awesome Main App !", color = MaterialTheme.colorScheme.onBackground)
                        }
                    }

                    // Restore & raise when visibility toggles to true
                    LaunchedEffect(isWindowVisible) {
                        if (isWindowVisible) {
                            WindowRaise.forceFront(window, state)
                        }
                    }
                }
            }
        }
    }
}
