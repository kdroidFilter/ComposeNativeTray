package com.kdroid.composetray.utils

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

var allowComposeNativeTrayLogging: Boolean = false
var composeNativeTrayloggingLevel: ComposeNativeTrayLoggingLevel = ComposeNativeTrayLoggingLevel.VERBOSE

class ComposeNativeTrayLoggingLevel(val priority: Int) {
    companion object {
        val VERBOSE = ComposeNativeTrayLoggingLevel(0)
        val DEBUG = ComposeNativeTrayLoggingLevel(1)
        val INFO = ComposeNativeTrayLoggingLevel(2)
        val WARN = ComposeNativeTrayLoggingLevel(3)
        val ERROR = ComposeNativeTrayLoggingLevel(4)
    }
}

private const val COLOR_RED = "\u001b[31m"
private const val COLOR_AQUA = "\u001b[36m"
private const val COLOR_LIGHT_GRAY = "\u001b[37m"
private const val COLOR_ORANGE = "\u001b[38;2;255;165;0m"
private const val COLOR_RESET = "\u001b[0m"

// Time formatter
private val timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

private fun getCurrentTimestamp(): String {
    return LocalDateTime.now().format(timeFormatter)
}

internal fun debugln(message: () -> String) {
    if (allowComposeNativeTrayLogging && composeNativeTrayloggingLevel.priority <= ComposeNativeTrayLoggingLevel.DEBUG.priority) {
        println("[${getCurrentTimestamp()}] ${message()}")
    }
}

internal fun verboseln(message: () -> String) {
    if (allowComposeNativeTrayLogging && composeNativeTrayloggingLevel.priority <= ComposeNativeTrayLoggingLevel.VERBOSE.priority) {
        println("[${getCurrentTimestamp()}] ${message()}", COLOR_LIGHT_GRAY)
    }
}

internal fun infoln(message: () -> String) {
    if (allowComposeNativeTrayLogging && composeNativeTrayloggingLevel.priority <= ComposeNativeTrayLoggingLevel.INFO.priority) {
        println("[${getCurrentTimestamp()}] ${message()}", COLOR_AQUA)
    }
}

internal fun warnln(message: () -> String) {
    if (allowComposeNativeTrayLogging && composeNativeTrayloggingLevel.priority <= ComposeNativeTrayLoggingLevel.WARN.priority) {
        println("[${getCurrentTimestamp()}] ${message()}", COLOR_ORANGE)
    }
}

internal fun errorln(message: () -> String) {
    if (allowComposeNativeTrayLogging && composeNativeTrayloggingLevel.priority <= ComposeNativeTrayLoggingLevel.ERROR.priority) {
        println("[${getCurrentTimestamp()}] ${message()}", COLOR_RED)
    }
}

private fun println(message: String, color: String) {
    println(color + message + COLOR_RESET)
}