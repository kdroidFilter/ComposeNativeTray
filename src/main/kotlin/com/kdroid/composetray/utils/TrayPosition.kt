package com.kdroid.composetray.utils

import java.io.File
import java.util.*

enum class TrayPosition {
    TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT
}

fun convertPositionToCorner(x: Int, y: Int, width: Int, height: Int): TrayPosition {
    return when {
        x < width / 2 && y < height / 2 -> TrayPosition.TOP_LEFT
        x >= width / 2 && y < height / 2 -> TrayPosition.TOP_RIGHT
        x < width / 2 && y >= height / 2 -> TrayPosition.BOTTOM_LEFT
        else -> TrayPosition.BOTTOM_RIGHT
    }
}

private const val PROPERTIES_FILE = "tray_position.properties"
private const val POSITION_KEY = "TrayPosition"

fun saveTrayPosition(position: TrayPosition) {
    val properties = Properties()
    val file = File(PROPERTIES_FILE)
    if (file.exists()) {
        properties.load(file.inputStream())
    }
    properties.setProperty(POSITION_KEY, position.name)
    file.outputStream().use { properties.store(it, null) }
}

fun getTrayPosition(): TrayPosition? {
    val properties = Properties()
    val file = File(PROPERTIES_FILE)
    if (file.exists()) {
        properties.load(file.inputStream())
        val position = properties.getProperty(POSITION_KEY, null)
        return position?.let { TrayPosition.valueOf(it) }
    } else return when (PlatformUtils.currentOS) {
        OperatingSystem.WINDOWS -> TrayPosition.BOTTOM_RIGHT
        OperatingSystem.MAC, OperatingSystem.LINUX, OperatingSystem.UNKNOWN -> TrayPosition.TOP_LEFT
    }
}

