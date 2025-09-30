package com.kdroid.composetray.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Window
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
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
import com.kdroid.composetray.tray.api.TrayApp
import com.kdroid.composetray.tray.api.rememberTrayAppState
import com.kdroid.composetray.utils.WindowRaise
import com.kdroid.composetray.utils.allowComposeNativeTrayLogging
import composenativetray.demo.generated.resources.Res
import composenativetray.demo.generated.resources.icon
import io.github.kdroidfilter.platformtools.darkmodedetector.isSystemInDarkMode
import io.github.kdroidfilter.platformtools.darkmodedetector.mac.setMacOsAdaptiveTitleBar
import io.github.kdroidfilter.platformtools.darkmodedetector.windows.setWindowsAdaptiveTitleBar
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource

@OptIn(ExperimentalTrayAppApi::class)
fun main() {
    allowComposeNativeTrayLogging = true
    setMacOsAdaptiveTitleBar()
    application {
        var isWindowVisible by remember { mutableStateOf(true) }
        val coroutineScope = rememberCoroutineScope()

        // Create TrayAppState with initial settings
        val trayAppState = rememberTrayAppState(
            initialWindowSize = DpSize(300.dp, 500.dp),
            initiallyVisible = true
        )

        // Observe visibility changes
        val isTrayPopupVisible by trayAppState.isVisible.collectAsState()

        // Set up visibility change callback
        LaunchedEffect(trayAppState) {
            trayAppState.onVisibilityChanged { visible ->
                println("Tray popup visibility changed to: $visible")
            }
        }

        TrayApp(
            state = trayAppState,
            icon = Icons.Default.Window,
            tooltip = "TrayAppDemo",
            menu = {
                Item(
                    if (isWindowVisible) "Hide the app" else "Open the App",
                    onClick = {
                        isWindowVisible = !isWindowVisible
                    }
                )
                Divider()
                Item(
                    if (isTrayPopupVisible) "Hide popup" else "Show popup",
                    onClick = {
                        trayAppState.toggle()
                    }
                )
                Item(
                    "Resize popup to 400x600",
                    onClick = {
                        trayAppState.setWindowSize(400.dp, 600.dp)
                    }
                )
                Item(
                    "Resize popup to 250x350",
                    onClick = {
                        trayAppState.setWindowSize(DpSize(250.dp, 350.dp))
                    }
                )
                Divider()
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
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                    ) {
                        Text("Tray Companion App", color = MaterialTheme.colorScheme.onBackground)
                        var textFieldValue by remember { mutableStateOf("") }

                        TextField(
                            value = textFieldValue,
                            onValueChange = { textFieldValue = it },
                            placeholder = { Text("Enter some text") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Text(
                            "Status: ${if (isTrayPopupVisible) "Visible" else "Hidden"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { trayAppState.hide() },
                                enabled = isTrayPopupVisible
                            ) {
                                Text("Hide")
                            }

                            Button(
                                onClick = { trayAppState.show() },
                                enabled = !isTrayPopupVisible
                            ) {
                                Text("Show")
                            }
                        }
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
                window.setWindowsAdaptiveTitleBar()

                MaterialTheme(
                    colorScheme = if (isSystemInDarkMode()) darkColorScheme() else lightColorScheme()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                            .padding(16.dp),
                        contentAlignment = Center,
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                        ) {
                            Text(
                                "Main Application Window",
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )

                            Text(
                                "Tray popup is: ${if (isTrayPopupVisible) "Visible" else "Hidden"}",
                                color = MaterialTheme.colorScheme.onBackground
                            )

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        coroutineScope.launch {
                                            trayAppState.show()
                                        }
                                    }
                                ) {
                                    Text("Show Tray Popup")
                                }

                                Button(
                                    onClick = {
                                        coroutineScope.launch {
                                            trayAppState.hide()
                                        }
                                    }
                                ) {
                                    Text("Hide Tray Popup")
                                }

                                Button(
                                    onClick = {
                                        coroutineScope.launch {
                                            trayAppState.toggle()
                                        }
                                    }
                                ) {
                                    Text("Toggle Tray Popup")
                                }
                            }

                            Divider(modifier = Modifier.padding(vertical = 8.dp))

                            Text(
                                "Window Size Controls",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        trayAppState.setWindowSize(250.dp, 400.dp)
                                    }
                                ) {
                                    Text("Small")
                                }

                                Button(
                                    onClick = {
                                        trayAppState.setWindowSize(350.dp, 500.dp)
                                    }
                                ) {
                                    Text("Medium")
                                }

                                Button(
                                    onClick = {
                                        trayAppState.setWindowSize(450.dp, 600.dp)
                                    }
                                ) {
                                    Text("Large")
                                }
                            }

                            val windowSize by trayAppState.windowSize.collectAsState()
                            Text(
                                "Current popup size: ${windowSize.width.value.toInt()} x ${windowSize.height.value.toInt()} dp",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onBackground
                            )
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