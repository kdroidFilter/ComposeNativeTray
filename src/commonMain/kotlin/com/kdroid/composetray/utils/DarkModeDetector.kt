package com.kdroid.composetray.utils

// New composable API (add to a file like DarkModeDetector.kt or utils)
// Assume package com.kdroid.composetray.utils or where isMacOsInDarkMode is defined

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import com.kdroid.composetray.lib.mac.MacOSMenuBarThemeDetector
import io.github.kdroidfilter.platformtools.getOperatingSystem
import io.github.kdroidfilter.platformtools.OperatingSystem.MACOS
import java.util.function.Consumer

@Composable
fun isMenuInDarkMode(): Boolean {
    return when (getOperatingSystem()) {
        MACOS -> isMacOsMenuBarInDarkMode()
        else -> true
    }
}

@Composable
internal fun isMacOsMenuBarInDarkMode(): Boolean {
    val darkModeState = remember { mutableStateOf(MacOSMenuBarThemeDetector.isDark()) }
    DisposableEffect(Unit) {
        val listener = Consumer<Boolean> { newValue ->
            darkModeState.value = newValue
        }
        MacOSMenuBarThemeDetector.registerListener(listener)
        onDispose {
            MacOSMenuBarThemeDetector.removeListener(listener)
        }
    }
    return darkModeState.value
}