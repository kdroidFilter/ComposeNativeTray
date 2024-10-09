package com.kdroid.composetray.utils

import java.util.*

enum class OperatingSystem {
    WINDOWS, MAC, LINUX, UNKNOWN
}

object PlatformUtils {
    val currentOS: OperatingSystem
        get() {
            val osName = System.getProperty("os.name").lowercase(Locale.getDefault())
            return when {
                osName.contains("win") -> OperatingSystem.WINDOWS
                osName.contains("mac") -> OperatingSystem.MAC
                osName.contains("nix") || osName.contains("nux") || osName.contains("aix") -> OperatingSystem.LINUX
                else -> OperatingSystem.UNKNOWN
            }
        }
}
