package com.kdroid.composetray.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Minimize
import androidx.compose.material.icons.filled.Window
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment.Companion.Center
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.kdroid.composetray.tray.api.TrayApp
import com.kdroid.composetray.utils.WindowRaise
import composenativetray.demo.generated.resources.Res
import composenativetray.demo.generated.resources.icon
import io.github.kdroidfilter.platformtools.darkmodedetector.isSystemInDarkMode
import io.github.kdroidfilter.platformtools.darkmodedetector.mac.setMacOsAdaptiveTitleBar
import io.github.kdroidfilter.platformtools.darkmodedetector.windows.setWindowsAdaptiveTitleBar
import org.jetbrains.compose.resources.painterResource

fun main() {
    setMacOsAdaptiveTitleBar()
    application {
        var isWindowVisible by remember { mutableStateOf(true) }
        var textFieldValue by remember { mutableStateOf("") }

        TrayApp(
            icon = Icons.Default.Book,
            tooltip = "TrayAppDemo",
            windowSize = DpSize(300.dp, 500.dp),
            transparent = true,
            visibleOnStart = true,
            menu = {
                Item(
                    if (isWindowVisible) "Hide the app" else "Open the App",
                    icon = if (isWindowVisible) Icons.Default.Minimize else Icons.Default.Window,
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
