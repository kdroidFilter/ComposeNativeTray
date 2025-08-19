package io.github.kdroidfilter.seforimapp.features.screens.bookcontent.state

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment.Companion.Center
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.application
import com.kdroid.composetray.tray.api.TrayApp

fun main() {
    application {
        TrayApp(
            icon = Icons.Default.Book,
            tooltip = "SeforimApp",
            windowSize = DpSize(300.dp, 500.dp),
            transparent = true,
            visibleOnStart = true,
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
                Item("Quitter", onClick = { exitApplication() })
            }
        )
    }
}