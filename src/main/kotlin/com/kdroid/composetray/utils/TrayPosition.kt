package com.kdroid.composetray.utils

import WindowsNativeTrayLibrary
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import com.sun.jna.Native
import java.awt.Toolkit
import java.io.File
import java.util.*

enum class TrayPosition {
    TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT
}

internal fun convertPositionToCorner(x: Int, y: Int, width: Int, height: Int): TrayPosition {
    return when {
        x < width / 2 && y < height / 2 -> TrayPosition.TOP_LEFT
        x >= width / 2 && y < height / 2 -> TrayPosition.TOP_RIGHT
        x < width / 2 && y >= height / 2 -> TrayPosition.BOTTOM_LEFT
        else -> TrayPosition.BOTTOM_RIGHT
    }
}

private const val PROPERTIES_FILE = "tray_position.properties"
private const val POSITION_KEY = "TrayPosition"

internal fun saveTrayPosition(position: TrayPosition) {
    val properties = Properties()
    val file = File(PROPERTIES_FILE)
    if (file.exists()) {
        properties.load(file.inputStream())
    }
    properties.setProperty(POSITION_KEY, position.name)
    file.outputStream().use { properties.store(it, null) }
}

internal fun getWindowsTrayPosition(nativeResult: String?): TrayPosition {
    return when (nativeResult) {
        null -> throw IllegalArgumentException("La valeur retournée est nulle")
        "top-left" -> TrayPosition.TOP_LEFT
        "top-right" -> TrayPosition.TOP_RIGHT
        "bottom-left" -> TrayPosition.BOTTOM_LEFT
        "bottom-right" -> TrayPosition.BOTTOM_RIGHT
        else -> throw IllegalArgumentException("Valeur inconnue : $nativeResult")
    }
}

fun getTrayPosition(): TrayPosition {
    when (PlatformUtils.currentOS) {
        OperatingSystem.WINDOWS -> {
            val trayLib: WindowsNativeTrayLibrary = Native.load("tray", WindowsNativeTrayLibrary::class.java)
            return getWindowsTrayPosition(trayLib.tray_get_notification_icons_region())
        }
        OperatingSystem.MAC -> return TrayPosition.TOP_RIGHT //Todo
        OperatingSystem.LINUX -> {
            val properties = Properties()
            val file = File(PROPERTIES_FILE)
            if (file.exists()) {
                properties.load(file.inputStream())
                val position = properties.getProperty(POSITION_KEY, null)
                return TrayPosition.valueOf(position)
            }
        }
        OperatingSystem.UNKNOWN -> return TrayPosition.TOP_RIGHT
    }
    return TrayPosition.TOP_RIGHT
}

fun getTrayWindowPosition(windowWidth: Int, windowHeight: Int): WindowPosition {
    val trayPosition = getTrayPosition()
    val screenSize = Toolkit.getDefaultToolkit().screenSize
    return when (trayPosition) {
        TrayPosition.TOP_LEFT -> WindowPosition(x = 0.dp, y = 0.dp)
        TrayPosition.TOP_RIGHT -> WindowPosition(x = (screenSize.width - windowWidth).dp, y = 0.dp)
        TrayPosition.BOTTOM_LEFT -> WindowPosition(x = 0.dp, y = (screenSize.height - windowHeight).dp)
        TrayPosition.BOTTOM_RIGHT -> WindowPosition(
            x = (screenSize.width - windowWidth).dp,
            y = (screenSize.height - windowHeight).dp
        )
    }
}