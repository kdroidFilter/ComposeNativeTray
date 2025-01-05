package com.kdroid.composetray.utils

import java.util.*

internal enum class OperatingSystem {
    WINDOWS, MAC, LINUX, UNKNOWN
}

/**
 * Utility object for determining the current operating system.
 *
 * The `PlatformUtils` object provides a mechanism to identify the user's operating system based on the system property
 * `os.name`. It offers a computed property `currentOS` that maps the operating system name to a corresponding
 * value from the `OperatingSystem` enum.
 *
 * The supported operating systems are:
 * - `WINDOWS`: Identified if the `os.name` contains "win".
 * - `MAC`: Identified if the `os.name` contains "mac".
 * - `LINUX`: Identified if the `os.name` contains "nix", "nux", or "aix".
 * - `UNKNOWN`: Used as a fallback when no known pattern matches the `os.name`.
 */
internal object PlatformUtils {
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
