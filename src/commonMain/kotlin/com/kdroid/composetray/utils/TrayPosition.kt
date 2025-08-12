package com.kdroid.composetray.utils

import com.kdroid.composetray.lib.windows.WindowsNativeTrayLibrary
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import com.kdroid.composetray.lib.mac.MacTrayLoader
import com.kdroid.composetray.lib.mac.MacTrayManager
import com.sun.jna.Native
import com.sun.jna.ptr.IntByReference
import io.github.kdroidfilter.platformtools.LinuxDesktopEnvironment
import io.github.kdroidfilter.platformtools.OperatingSystem
import io.github.kdroidfilter.platformtools.detectLinuxDesktopEnvironment
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

    fun setClickPosition(x: Int, y: Int, position: TrayPosition) {
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

// Use a writable tmp/cache directory to avoid read-only filesystem issues (e.g., macOS app bundle working dir)
private fun trayPropertiesFile(): File {
    val tmpBase = System.getProperty("java.io.tmpdir") ?: "."
    val appDir = File(File(tmpBase, "ComposeNativeTray"), AppIdProvider.appId())
    // Best-effort create directory; if it fails, we will fallback to legacy file access patterns safely
    runCatching { if (!appDir.exists()) appDir.mkdirs() }.onFailure { /* ignore */ }
    return File(appDir, PROPERTIES_FILE)
}

// Legacy properties file in working directory (for backward compatibility)
private fun legacyPropertiesFile(): File = File(PROPERTIES_FILE)

// Previous tmp location before appId namespacing (for backward-compatible reads only)
private fun oldTmpPropertiesFile(): File {
    val tmpBase = System.getProperty("java.io.tmpdir") ?: "."
    val oldDir = File(tmpBase, "ComposeNativeTray")
    return File(oldDir, PROPERTIES_FILE)
}

private fun loadPropertiesFrom(file: File): Properties? {
    if (!file.exists()) return null
    return runCatching {
        Properties().apply { file.inputStream().use { load(it) } }
    }.getOrNull()
}

private fun storePropertiesTo(file: File, props: Properties) {
    // Ensure parent exists if any
    file.parentFile?.let { runCatching { if (!it.exists()) it.mkdirs() } }
    runCatching { file.outputStream().use { props.store(it, null) } }
    // We intentionally swallow exceptions to avoid crashing if the FS is read-only
}

internal fun saveTrayPosition(position: TrayPosition) {
    val preferredFile = trayPropertiesFile()
    val properties = loadPropertiesFrom(preferredFile)
        ?: loadPropertiesFrom(oldTmpPropertiesFile())
        ?: loadPropertiesFrom(legacyPropertiesFile())
        ?: Properties()
    properties.setProperty(POSITION_KEY, position.name)
    storePropertiesTo(preferredFile, properties)
}

internal fun saveTrayClickPosition(x: Int, y: Int, position: TrayPosition) {
    val preferredFile = trayPropertiesFile()
    val properties = loadPropertiesFrom(preferredFile)
        ?: loadPropertiesFrom(oldTmpPropertiesFile())
        ?: loadPropertiesFrom(legacyPropertiesFile())
        ?: Properties()
    properties.setProperty(POSITION_KEY, position.name)
    properties.setProperty(X_KEY, x.toString())
    properties.setProperty(Y_KEY, y.toString())
    storePropertiesTo(preferredFile, properties)
}

internal fun loadTrayClickPosition(): TrayClickPosition? {
    // Prefer new location, fallback to old tmp location, then legacy working dir
    val props = loadPropertiesFrom(trayPropertiesFile())
        ?: loadPropertiesFrom(oldTmpPropertiesFile())
        ?: loadPropertiesFrom(legacyPropertiesFile()) ?: return null

    val positionStr = props.getProperty(POSITION_KEY) ?: return null
    val x = props.getProperty(X_KEY)?.toIntOrNull() ?: return null
    val y = props.getProperty(Y_KEY)?.toIntOrNull() ?: return null

    return try {
        TrayClickPosition(x, y, TrayPosition.valueOf(positionStr))
    } catch (e: IllegalArgumentException) {
        null
    }
}

internal fun getWindowsTrayPosition(nativeResult: String?): TrayPosition {
    return when (nativeResult) {
        null -> throw IllegalArgumentException("Returned value is null")
        "top-left" -> TrayPosition.TOP_LEFT
        "top-right" -> TrayPosition.TOP_RIGHT
        "bottom-left" -> TrayPosition.BOTTOM_LEFT
        "bottom-right" -> TrayPosition.BOTTOM_RIGHT
        else -> throw IllegalArgumentException("Unknown value: $nativeResult")
    }
}

/**
 * Determines the position of the system tray icons based on the current operating system.
 *
 * The method evaluates the operating system in use and retrieves the corresponding tray position.
 * - On Windows, it uses the platform's native library to determine the tray position.
 * - On macOS, it defaults to a specific standard position.
 * - On Linux, the position is fetched from click coordinates or properties file.
 *   If no position data is available, it uses desktop environment-specific defaults:
 *   - GNOME: TOP_RIGHT
 *   - KDE: BOTTOM_RIGHT
 *   - XFCE: TOP_RIGHT
 *   - CINNAMON: BOTTOM_RIGHT
 *   - MATE: TOP_RIGHT
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
        OperatingSystem.MACOS -> {
            val lib = MacTrayLoader.lib
            return getMacTrayPosition(lib.tray_get_status_item_region())
        }
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
            run {
                val props = loadPropertiesFrom(trayPropertiesFile())
                    ?: loadPropertiesFrom(oldTmpPropertiesFile())
                    ?: loadPropertiesFrom(legacyPropertiesFile())
                val position = props?.getProperty(POSITION_KEY, null)
                if (position != null) {
                    return try {
                        TrayPosition.valueOf(position)
                    } catch (e: IllegalArgumentException) {
                        TrayPosition.TOP_RIGHT
                    }
                }
            }
            
            // If no position is found, use desktop environment-specific defaults
            return when (detectLinuxDesktopEnvironment()) {
                LinuxDesktopEnvironment.GNOME -> TrayPosition.TOP_RIGHT
                LinuxDesktopEnvironment.KDE -> TrayPosition.BOTTOM_RIGHT
                LinuxDesktopEnvironment.XFCE -> TrayPosition.TOP_RIGHT
                LinuxDesktopEnvironment.CINNAMON -> TrayPosition.BOTTOM_RIGHT
                LinuxDesktopEnvironment.MATE -> TrayPosition.TOP_RIGHT
                LinuxDesktopEnvironment.UNKNOWN -> TrayPosition.TOP_RIGHT
                null -> TrayPosition.TOP_RIGHT
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

    // For Windows, try to use exact click coordinates, fallback to native and save like Linux
    if (getOperatingSystem() == OperatingSystem.WINDOWS) {
        // 1) on tente de récupérer ce qu’on a déjà
        val cachedPos = TrayClickTracker.getLastClickPosition() ?: loadTrayClickPosition()

    // 2) on interroge la DLL (asynchrone, peut échouer)
        getNotificationAreaXYForWindows()

        // 3) on choisit la meilleure info disponible
        val freshPos = TrayClickTracker.getLastClickPosition()
        val posToUse = cachedPos ?: freshPos
        ?: return fallbackCornerPosition(windowWidth, windowHeight) // aucun point fiable

        return calculateWindowPositionFromClick(
            posToUse.x, posToUse.y, posToUse.position,
            windowWidth, windowHeight,
            screenSize.width, screenSize.height
        )

    }

    if (getOperatingSystem() == OperatingSystem.MACOS) {
        // 1) cache éventuel
        val cached = TrayClickTracker.getLastClickPosition() ?: loadTrayClickPosition()

        // 2) interroge Cocoa
        val (x0, y0) = getStatusItemXYForMac()
        if (x0 != 0 || y0 != 0) {
            TrayClickTracker.setClickPosition(
                x0, y0,
                getTrayPosition()        // TOP_LEFT ou TOP_RIGHT
            )
        }

        // 3) choisit la meilleure info
        val pos = TrayClickTracker.getLastClickPosition() ?: cached
        if (pos != null) {
            return calculateWindowPositionFromClick(
                pos.x, pos.y, pos.position,
                windowWidth, windowHeight,
                screenSize.width, screenSize.height
            )
        }
    }


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
        clickY
    } else {
        // Tray at bottom: position window above icon
        clickY - windowHeight
    }

    // Ensure window stays within screen bounds vertically
    if (y < 0) {
        y = 0
    } else if (y + windowHeight > screenHeight) {
        y = screenHeight - windowHeight
    }

    return WindowPosition(x = x.dp, y = y.dp)
}

/**
 * Interroge la DLL native pour récupérer le coin (x, y) de la zone de notification Windows.
 */
fun getNotificationAreaXYForWindows(): Pair<Int, Int> {
    val xRef = IntByReference()
    val yRef = IntByReference()

    val trayLib: WindowsNativeTrayLibrary =
        Native.load("tray", WindowsNativeTrayLibrary::class.java)

    val precise = trayLib.tray_get_notification_icons_position(xRef, yRef) != 0

    val x = xRef.value
    val y = yRef.value

    // On ne mémorise la coordonnée que si elle est fiable
    if (precise) {
        val trayPosition = getTrayPosition()          // TOP_LEFT, TOP_RIGHT, ...
        TrayClickTracker.setClickPosition(x, y, trayPosition)
    }

    debugln {
        "Notification area : ($x, $y) " +
                if (precise) "[exact]" else "[fallback]"
    }
    return x to y
}

private fun fallbackCornerPosition(w: Int, h: Int): WindowPosition {
    val screen = Toolkit.getDefaultToolkit().screenSize
    return when (getTrayPosition()) {
        TrayPosition.TOP_LEFT     -> WindowPosition(0.dp, 0.dp)
        TrayPosition.TOP_RIGHT    -> WindowPosition((screen.width - w).dp, 0.dp)
        TrayPosition.BOTTOM_LEFT  -> WindowPosition(0.dp, (screen.height - h).dp)
        TrayPosition.BOTTOM_RIGHT -> WindowPosition(
            (screen.width - w).dp, (screen.height - h).dp
        )
    }
}

internal fun getMacTrayPosition(nativeResult: String?): TrayPosition = when (nativeResult) {
    "top-left"     -> TrayPosition.TOP_LEFT
    "top-right"    -> TrayPosition.TOP_RIGHT
    // on ne verra jamais TOP/BOTTOM_BOTTOM sur mac, mais pour rester cohérent :
    else           -> TrayPosition.TOP_RIGHT
}

internal fun getStatusItemXYForMac(): Pair<Int, Int> {
    val xRef = IntByReference()
    val yRef = IntByReference()
    val lib = MacTrayLoader.lib

    val precise = lib.tray_get_status_item_position(xRef, yRef) != 0
    return xRef.value to yRef.value    // si !precise, valeurs = (0,0)
}
