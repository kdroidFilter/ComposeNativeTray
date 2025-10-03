package com.kdroid.composetray.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Window
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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
import com.kdroid.composetray.tray.api.TrayWindowDismissMode
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
            initiallyVisible = true,
            dismissMode = TrayWindowDismissMode.AUTO // Start with AUTO mode
        )

        // Observe state changes
        val isTrayPopupVisible by trayAppState.isVisible.collectAsState()
        val currentDismissMode by trayAppState.dismissMode.collectAsState()
        val windowSize by trayAppState.windowSize.collectAsState()

        // Set up visibility change callback
        LaunchedEffect(trayAppState) {
            trayAppState.onVisibilityChanged { visible ->
                println("Tray popup visibility changed to: $visible")
            }
        }

        TrayApp(
            icon = Icons.Default.Window,
            state = trayAppState,
            tooltip = "TrayAppDemo",
            fadeDurationMs = 200,
            menu = {
                Item(
                    if (isWindowVisible) "Hide Main Window" else "Show Main Window",
                    onClick = {
                        isWindowVisible = !isWindowVisible
                    }
                )
                Divider()
                Item(
                    if (isTrayPopupVisible) "Hide Popup" else "Show Popup",
                    onClick = {
                        trayAppState.toggle()
                    }
                )
                Divider()
                Item(
                    "Dismiss Mode: $currentDismissMode",
                    isEnabled = false
                )
                Item(
                    "Switch to ${if (currentDismissMode == TrayWindowDismissMode.AUTO) "MANUAL" else "AUTO"} mode",
                    onClick = {
                        val newMode = if (currentDismissMode == TrayWindowDismissMode.AUTO) {
                            TrayWindowDismissMode.MANUAL
                        } else {
                            TrayWindowDismissMode.AUTO
                        }
                        trayAppState.setDismissMode(newMode)
                        println("Switched dismiss mode to: $newMode")
                    }
                )
                Divider()
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
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Tray Popup Window",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onBackground
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Show current dismiss mode
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    "Dismiss Mode: ${currentDismissMode.name}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    when (currentDismissMode) {
                                        TrayWindowDismissMode.AUTO -> "Window closes on focus loss or outside click"
                                        TrayWindowDismissMode.MANUAL -> "Window stays open until manually closed"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Text field to test focus behavior
                        var textFieldValue by remember { mutableStateOf("") }
                        TextField(
                            value = textFieldValue,
                            onValueChange = { textFieldValue = it },
                            label = { Text("Test focus behavior") },
                            placeholder = { Text("Type here...") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Window size info
                        Text(
                            "Window Size: ${windowSize.width.value.toInt()} × ${windowSize.height.value.toInt()} dp",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Control buttons
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    val newMode = if (currentDismissMode == TrayWindowDismissMode.AUTO) {
                                        TrayWindowDismissMode.MANUAL
                                    } else {
                                        TrayWindowDismissMode.AUTO
                                    }
                                    trayAppState.setDismissMode(newMode)
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    "Switch to ${
                                        if (currentDismissMode == TrayWindowDismissMode.AUTO) "MANUAL" else "AUTO"
                                    }",
                                    maxLines = 1
                                )
                            }
                        }

                        if (currentDismissMode == TrayWindowDismissMode.MANUAL) {
                            Button(
                                onClick = { trayAppState.hide() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Close Window (Manual Mode)")
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Info text
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer
                            )
                        ) {
                            Text(
                                text = if (currentDismissMode == TrayWindowDismissMode.AUTO) {
                                    "💡 Click outside or switch focus to close"
                                } else {
                                    "💡 Click tray icon or use button to close"
                                },
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
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
                title = "Main Application",
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
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "Main Application Window",
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )

                            Card(
                                modifier = Modifier.fillMaxWidth(0.8f),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isTrayPopupVisible)
                                        MaterialTheme.colorScheme.primaryContainer
                                    else
                                        MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        "Tray Popup Status",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        if (isTrayPopupVisible) "VISIBLE" else "HIDDEN",
                                        style = MaterialTheme.typography.headlineSmall,
                                        color = if (isTrayPopupVisible)
                                            MaterialTheme.colorScheme.primary
                                        else
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        "Mode: ${currentDismissMode.name}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            HorizontalDivider(modifier = Modifier.fillMaxWidth(0.5f))

                            Text(
                                "Visibility Controls",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { trayAppState.show() },
                                    enabled = !isTrayPopupVisible
                                ) {
                                    Text("Show")
                                }

                                Button(
                                    onClick = { trayAppState.hide() },
                                    enabled = isTrayPopupVisible
                                ) {
                                    Text("Hide")
                                }

                                Button(
                                    onClick = { trayAppState.toggle() }
                                ) {
                                    Text("Toggle")
                                }
                            }

                            HorizontalDivider(modifier = Modifier.fillMaxWidth(0.5f))

                            Text(
                                "Dismiss Mode Controls",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { trayAppState.setDismissMode(TrayWindowDismissMode.AUTO) },
                                    enabled = currentDismissMode != TrayWindowDismissMode.AUTO
                                ) {
                                    Text("AUTO Mode")
                                }

                                OutlinedButton(
                                    onClick = { trayAppState.setDismissMode(TrayWindowDismissMode.MANUAL) },
                                    enabled = currentDismissMode != TrayWindowDismissMode.MANUAL
                                ) {
                                    Text("MANUAL Mode")
                                }
                            }

                            HorizontalDivider(modifier = Modifier.fillMaxWidth(0.5f))

                            Text(
                                "Window Size Controls",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilledTonalButton(
                                    onClick = { trayAppState.setWindowSize(250.dp, 400.dp) }
                                ) {
                                    Text("Small")
                                }

                                FilledTonalButton(
                                    onClick = { trayAppState.setWindowSize(350.dp, 500.dp) }
                                ) {
                                    Text("Medium")
                                }

                                FilledTonalButton(
                                    onClick = { trayAppState.setWindowSize(450.dp, 600.dp) }
                                ) {
                                    Text("Large")
                                }
                            }

                            Text(
                                "Current popup size: ${windowSize.width.value.toInt()} × ${windowSize.height.value.toInt()} dp",
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