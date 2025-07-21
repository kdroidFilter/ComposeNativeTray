package com.kdroid.composetray.utils

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

internal fun debugln(message: () -> String) {
    if (allowComposeNativeTrayLogging && composeNativeTrayloggingLevel.priority <= ComposeNativeTrayLoggingLevel.DEBUG.priority) {
        println(message())
    }
}

internal fun verboseln(message: () -> String) {
    if (allowComposeNativeTrayLogging && composeNativeTrayloggingLevel.priority <= ComposeNativeTrayLoggingLevel.VERBOSE.priority) {
        println(message(), COLOR_LIGHT_GRAY)
    }
}


internal fun infoln(message: () -> String) {
    if (allowComposeNativeTrayLogging && composeNativeTrayloggingLevel.priority <= ComposeNativeTrayLoggingLevel.INFO.priority) {
        println(message(), COLOR_AQUA)
    }
}

internal fun warnln(message: () -> String) {
    if (allowComposeNativeTrayLogging && composeNativeTrayloggingLevel.priority <= ComposeNativeTrayLoggingLevel.WARN.priority) {
        println(message(), COLOR_ORANGE)
    }
}

internal fun errorln(message: () -> String) {
    if (allowComposeNativeTrayLogging && composeNativeTrayloggingLevel.priority <= ComposeNativeTrayLoggingLevel.ERROR.priority) {
        println(message(), COLOR_RED)
    }
}

private fun println(message: String, color: String) {
    println(color + message + COLOR_RESET)
}