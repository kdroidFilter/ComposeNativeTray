package com.kdroid.composetray.utils

import com.kdroid.composetray.lib.windows.WindowsNativeTrayLibrary
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import com.sun.jna.Native
import io.github.kdroidfilter.platformtools.OperatingSystem
import io.github.kdroidfilter.platformtools.getOperatingSystem
import java.awt.Toolkit
import java.io.File
import java.util.*
import java.util.concurrent.atomic.AtomicReference

enum class TrayPosition {
    TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT
}

// Data class to store exact tray click coordinates
data class TrayClickPosition(
    val x: Int,
    val y: Int,
    val position: TrayPosition
)

// Global storage for last click position
internal object TrayClickTracker {
    private val lastClickPosition = AtomicReference<TrayClickPosition?>(null)

    fun updateClickPosition(x: Int, y: Int) {
        val screenSize = Toolkit.getDefaultToolkit().screenSize
        val position = convertPositionToCorner(x, y, screenSize.width, screenSize.height)
        lastClickPosition.set(TrayClickPosition(x, y, position))
        saveTrayClickPosition(x, y, position)
    }

    fun getLastClickPosition(): TrayClickPosition? = lastClickPosition.get()
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
private const val X_KEY = "TrayX"
private const val Y_KEY = "TrayY"

internal fun saveTrayPosition(position: TrayPosition) {
    val properties = Properties()
    val file = File(PROPERTIES_FILE)
    if (file.exists()) {
        properties.load(file.inputStream())
    }
    properties.setProperty(POSITION_KEY, position.name)
    file.outputStream().use { properties.store(it, null) }
}

internal fun saveTrayClickPosition(x: Int, y: Int, position: TrayPosition) {
    val properties = Properties()
    val file = File(PROPERTIES_FILE)
    if (file.exists()) {
        properties.load(file.inputStream())
    }
    properties.setProperty(POSITION_KEY, position.name)
    properties.setProperty(X_KEY, x.toString())
    properties.setProperty(Y_KEY, y.toString())
    file.outputStream().use { properties.store(it, null) }
}

internal fun loadTrayClickPosition(): TrayClickPosition? {
    val file = File(PROPERTIES_FILE)
    if (!file.exists()) return null

    val properties = Properties()
    properties.load(file.inputStream())

    val positionStr = properties.getProperty(POSITION_KEY) ?: return null
    val x = properties.getProperty(X_KEY)?.toIntOrNull() ?: return null
    val y = properties.getProperty(Y_KEY)?.toIntOrNull() ?: return null

    return try {
        TrayClickPosition(x, y, TrayPosition.valueOf(positionStr))
    } catch (e: IllegalArgumentException) {
        null
    }
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

/**
 * Determines the position of the system tray icons based on the current operating system.
 *
 * The method evaluates the operating system in use and retrieves the corresponding tray position.
 * - On Windows, it uses the platform's native library to determine the tray position.
 * - On macOS, it defaults to a specific standard position.
 * - On Linux, the position is fetched from click coordinates or properties file.
 * - For unknown or unsupported operating systems, a default position is returned.
 *
 * @return The computed tray position as a [TrayPosition] enum value.
 */
fun getTrayPosition(): TrayPosition {
    when (getOperatingSystem()) {
        OperatingSystem.WINDOWS -> {
            val trayLib: WindowsNativeTrayLibrary = Native.load("tray", WindowsNativeTrayLibrary::class.java)
            return getWindowsTrayPosition(trayLib.tray_get_notification_icons_region())
        }
        OperatingSystem.MACOS -> return TrayPosition.TOP_RIGHT //Todo
        OperatingSystem.LINUX -> {
            // First check if we have a recent click position in memory
            TrayClickTracker.getLastClickPosition()?.let {
                return it.position
            }

            // Otherwise, try to load from properties file
            loadTrayClickPosition()?.let {
                return it.position
            }

            // Legacy fallback - just position without coordinates
            val properties = Properties()
            val file = File(PROPERTIES_FILE)
            if (file.exists()) {
                properties.load(file.inputStream())
                val position = properties.getProperty(POSITION_KEY, null)
                if (position != null) {
                    return try {
                        TrayPosition.valueOf(position)
                    } catch (e: IllegalArgumentException) {
                        TrayPosition.TOP_RIGHT
                    }
                }
            }
        }
        OperatingSystem.UNKNOWN -> return TrayPosition.TOP_RIGHT
        else -> {}
    }
    return TrayPosition.TOP_RIGHT
}

/**
 * Calculates the position of a tray window on the screen based on the current tray position and given window dimensions.
 *
 * This method determines the coordinates (x, y) where the tray window should be positioned, ensuring alignment
 * with the current system tray's placement. For Linux, it uses the exact click coordinates when available.
 *
 * @param windowWidth The width of the tray window in pixels.
 * @param windowHeight The height of the tray window in pixels.
 * @return The calculated position as a [WindowPosition] object containing the x and y coordinates.
 */
fun getTrayWindowPosition(windowWidth: Int, windowHeight: Int): WindowPosition {
    val screenSize = Toolkit.getDefaultToolkit().screenSize

    // For Linux, try to use exact click coordinates
    if (getOperatingSystem() == OperatingSystem.LINUX) {
        val clickPos = TrayClickTracker.getLastClickPosition() ?: loadTrayClickPosition()
        if (clickPos != null) {
            return calculateWindowPositionFromClick(
                clickPos.x, clickPos.y, clickPos.position,
                windowWidth, windowHeight,
                screenSize.width, screenSize.height
            )
        }
    }

    // Fallback to corner-based positioning
    val trayPosition = getTrayPosition()
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

/**
 * Calculate window position based on exact click coordinates.
 * This positions the window centered horizontally on the click point (icon),
 * and vertically above or below based on tray position, while ensuring it stays within screen bounds.
 */
private fun calculateWindowPositionFromClick(
    clickX: Int, clickY: Int, trayPosition: TrayPosition,
    windowWidth: Int, windowHeight: Int,
    screenWidth: Int, screenHeight: Int
): WindowPosition {
    val margin = 10 // Small margin from the tray icon

    // Horizontal: center on the icon
    var x = clickX - (windowWidth / 2)

    // Adjust if it goes off screen
    if (x < 0) {
        x = 0
    } else if (x + windowWidth > screenWidth) {
        x = screenWidth - windowWidth
    }

    // Vertical: depend on tray position (top or bottom)
    var y = if (trayPosition == TrayPosition.TOP_LEFT || trayPosition == TrayPosition.TOP_RIGHT) {
        // Tray at top: position window below icon
        clickY + margin
    } else {
        // Tray at bottom: position window above icon
        clickY - windowHeight - margin
    }

    // Ensure window stays within screen bounds vertically
    if (y < 0) {
        y = 0
    } else if (y + windowHeight > screenHeight) {
        y = screenHeight - windowHeight
    }

    return WindowPosition(x = x.dp, y = y.dp)
}