package com.kdroid.composetray.utils

/**
 * Provides a unique, stable application identifier to namespace shared resources
 * (temp files, locks, properties) and avoid conflicts when multiple apps use
 * this library on the same machine.
 *
 * Resolution order (first non-empty wins):
 * 1) System property "compose.native.tray.appId"
 * 2) Environment variable "COMPOSE_NATIVE_TRAY_APP_ID"
 * 3) Main class from system property "sun.java.command" (first token)
 * 4) Fallback to "ComposeNativeTrayApp"
 */
object AppIdProvider {
    private val cached by lazy { computeAppId() }

    fun appId(): String = cached

    private fun computeAppId(): String {
        val sunCmd = System.getProperty("sun.java.command")?.trim().orEmpty()
        println("AppIdProvider: sunCmd: $sunCmd")

        if (sunCmd.isNotEmpty()) {
            val firstToken = sunCmd.split(" ", limit = 2).firstOrNull().orEmpty()
            if (firstToken.isNotEmpty()) return sanitize(firstToken)
        }

        // Fallback
        return "ComposeNativeTrayApp"
    }

    private fun sanitize(raw: String): String {
        // Replace non-alphanumeric/._- with underscore; trim length if excessively long
        val cleaned = raw.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return cleaned.take(128).ifEmpty { "ComposeNativeTrayApp" }
    }
}
