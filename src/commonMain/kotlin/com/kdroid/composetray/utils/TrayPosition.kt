package com.kdroid.composetray.utils

import com.kdroid.composetray.lib.windows.WindowsNativeTrayLibrary
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import com.kdroid.composetray.lib.mac.MacTrayLoader
import com.kdroid.composetray.tray.impl.MacTrayInitializer
import com.sun.jna.ptr.IntByReference
import io.github.kdroidfilter.platformtools.LinuxDesktopEnvironment
import io.github.kdroidfilter.platformtools.OperatingSystem
import io.github.kdroidfilter.platformtools.detectLinuxDesktopEnvironment
import io.github.kdroidfilter.platformtools.getOperatingSystem
import java.awt.Toolkit
import java.io.File
import java.util.*
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.roundToInt

enum class TrayPosition { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

data class TrayClickPosition(
    val x: Int,
    val y: Int,
    val position: TrayPosition
)

internal object TrayClickTracker {
    private val lastClickPosition = AtomicReference<TrayClickPosition?>(null)
    private val perInstancePositions: MutableMap<String, TrayClickPosition> =
        Collections.synchronizedMap(mutableMapOf())

    fun updateClickPosition(x: Int, y: Int) {
        val screenSize = Toolkit.getDefaultToolkit().screenSize
        val position = convertPositionToCorner(x, y, screenSize.width, screenSize.height)
        val pos = TrayClickPosition(x, y, position)
        lastClickPosition.set(pos)
        runCatching { saveTrayClickPosition(x, y, position) }
    }

    fun updateClickPosition(instanceId: String, x: Int, y: Int) {
        val screenSize = Toolkit.getDefaultToolkit().screenSize
        val position = convertPositionToCorner(x, y, screenSize.width, screenSize.height)
        val pos = TrayClickPosition(x, y, position)
        perInstancePositions[instanceId] = pos
        lastClickPosition.set(pos)
        runCatching { saveTrayClickPosition(x, y, position) }
    }

    fun setClickPosition(x: Int, y: Int, position: TrayPosition) {
        val pos = TrayClickPosition(x, y, position)
        lastClickPosition.set(pos)
        runCatching { saveTrayClickPosition(x, y, position) }
    }

    fun setClickPosition(instanceId: String, x: Int, y: Int, position: TrayPosition) {
        val pos = TrayClickPosition(x, y, position)
        perInstancePositions[instanceId] = pos
        lastClickPosition.set(pos)
        runCatching { saveTrayClickPosition(x, y, position) }
    }

    fun getLastClickPosition(): TrayClickPosition? = lastClickPosition.get()
    fun getLastClickPosition(instanceId: String): TrayClickPosition? = perInstancePositions[instanceId]
}

internal fun convertPositionToCorner(x: Int, y: Int, width: Int, height: Int): TrayPosition = when {
    x < width / 2 && y < height / 2 -> TrayPosition.TOP_LEFT
    x >= width / 2 && y < height / 2 -> TrayPosition.TOP_RIGHT
    x < width / 2 && y >= height / 2 -> TrayPosition.BOTTOM_LEFT
    else -> TrayPosition.BOTTOM_RIGHT
}

private const val PROPERTIES_FILE = "tray_position.properties"
private const val POSITION_KEY = "TrayPosition"
private const val X_KEY = "TrayX"
private const val Y_KEY = "TrayY"

private fun trayPropertiesFile(): File {
    val appId = AppIdProvider.appId()
    val tmpBase = System.getProperty("java.io.tmpdir") ?: "."
    val tmpDir = File(File(tmpBase, "ComposeNativeTray"), appId)
    val macCacheDir = macCacheDir()?.resolve(appId)
    val candidates = listOfNotNull(tmpDir, macCacheDir)
    for (dir in candidates) {
        runCatching { if (!dir.exists()) dir.mkdirs() }
        if (dir.exists() && dir.canWrite()) return File(dir, PROPERTIES_FILE)
    }
    return File(tmpDir, PROPERTIES_FILE)
}

private fun legacyPropertiesFile(): File = File(PROPERTIES_FILE)

private fun oldTmpPropertiesFile(): File {
    val tmpBase = System.getProperty("java.io.tmpdir") ?: "."
    val oldDir = File(tmpBase, "ComposeNativeTray")
    return File(oldDir, PROPERTIES_FILE)
}

private fun macCachePropertiesFile(): File? {
    val appId = AppIdProvider.appId()
    val dir = macCacheDir()?.resolve(appId) ?: return null
    return File(dir, PROPERTIES_FILE)
}

private fun macCacheDir(): File? {
    val userHome = System.getProperty("user.home") ?: return null
    return File(userHome).resolve("Library").resolve("Caches").resolve("ComposeNativeTray")
}

private fun loadPropertiesFrom(file: File): Properties? {
    if (!file.exists()) return null
    return runCatching {
        Properties().apply { file.inputStream().use(::load) }
    }.getOrNull()
}

private fun storePropertiesTo(file: File, props: Properties) {
    file.parentFile?.let { runCatching { if (!it.exists()) it.mkdirs() } }
    runCatching { file.outputStream().use { props.store(it, null) } }
}

internal fun saveTrayPosition(position: TrayPosition) {
    val preferredFile = trayPropertiesFile()
    val properties = loadPropertiesFrom(preferredFile)
        ?: macCachePropertiesFile()?.let { loadPropertiesFrom(it) }
        ?: loadPropertiesFrom(oldTmpPropertiesFile())
        ?: loadPropertiesFrom(legacyPropertiesFile())
        ?: Properties()
    properties.setProperty(POSITION_KEY, position.name)
    storePropertiesTo(preferredFile, properties)
}

internal fun saveTrayClickPosition(x: Int, y: Int, position: TrayPosition) {
    val preferredFile = trayPropertiesFile()
    val properties = loadPropertiesFrom(preferredFile)
        ?: macCachePropertiesFile()?.let { loadPropertiesFrom(it) }
        ?: loadPropertiesFrom(oldTmpPropertiesFile())
        ?: loadPropertiesFrom(legacyPropertiesFile())
        ?: Properties()
    properties.setProperty(POSITION_KEY, position.name)
    properties.setProperty(X_KEY, x.toString())
    properties.setProperty(Y_KEY, y.toString())
    storePropertiesTo(preferredFile, properties)
}

internal fun loadTrayClickPosition(): TrayClickPosition? {
    val props = loadPropertiesFrom(trayPropertiesFile())
        ?: macCachePropertiesFile()?.let { loadPropertiesFrom(it) }
        ?: loadPropertiesFrom(oldTmpPropertiesFile())
        ?: loadPropertiesFrom(legacyPropertiesFile()) ?: return null

    val positionStr = props.getProperty(POSITION_KEY) ?: return null
    val x = props.getProperty(X_KEY)?.toIntOrNull() ?: return null
    val y = props.getProperty(Y_KEY)?.toIntOrNull() ?: return null

    return try {
        TrayClickPosition(x, y, TrayPosition.valueOf(positionStr))
    } catch (_: IllegalArgumentException) {
        null
    }
}

internal fun getWindowsTrayPosition(nativeResult: String?): TrayPosition = when (nativeResult) {
    null -> throw IllegalArgumentException("Returned value is null")
    "top-left" -> TrayPosition.TOP_LEFT
    "top-right" -> TrayPosition.TOP_RIGHT
    "bottom-left" -> TrayPosition.BOTTOM_LEFT
    "bottom-right" -> TrayPosition.BOTTOM_RIGHT
    else -> throw IllegalArgumentException("Unknown value: $nativeResult")
}

/** OS → Tray corner heuristics */
fun getTrayPosition(): TrayPosition {
    return when (getOperatingSystem()) {
        OperatingSystem.WINDOWS -> getWindowsTrayPosition(WindowsNativeTrayLibrary.tray_get_notification_icons_region())
        OperatingSystem.MACOS -> getMacTrayPosition(MacTrayLoader.lib.tray_get_status_item_region())
        OperatingSystem.LINUX -> {
            TrayClickTracker.getLastClickPosition()?.position
                ?: loadTrayClickPosition()?.position
                ?: run {
                    val props = loadPropertiesFrom(trayPropertiesFile())
                        ?: macCachePropertiesFile()?.let { loadPropertiesFrom(it) }
                        ?: loadPropertiesFrom(oldTmpPropertiesFile())
                        ?: loadPropertiesFrom(legacyPropertiesFile())
                    props?.getProperty(POSITION_KEY)?.let {
                        runCatching { TrayPosition.valueOf(it) }.getOrNull()
                    }
                }
                ?: when (detectLinuxDesktopEnvironment()) {
                    LinuxDesktopEnvironment.KDE      -> TrayPosition.BOTTOM_RIGHT
                    LinuxDesktopEnvironment.CINNAMON -> TrayPosition.BOTTOM_RIGHT
                    LinuxDesktopEnvironment.GNOME    -> TrayPosition.TOP_RIGHT
                    LinuxDesktopEnvironment.MATE     -> TrayPosition.TOP_RIGHT
                    LinuxDesktopEnvironment.XFCE     -> TrayPosition.TOP_RIGHT
                    else                             -> TrayPosition.TOP_RIGHT
                }
        }
        OperatingSystem.UNKNOWN -> TrayPosition.TOP_RIGHT
        else -> TrayPosition.TOP_RIGHT
    }
}

/** Position globale (sans instance) + offsets */
fun getTrayWindowPosition(
    windowWidth: Int,
    windowHeight: Int,
    horizontalOffset: Int = 0,
    verticalOffset: Int = 0
): WindowPosition {
    val screenSize = Toolkit.getDefaultToolkit().screenSize

    if (getOperatingSystem() == OperatingSystem.WINDOWS) {
        val freshPos = TrayClickTracker.getLastClickPosition()
        val posToUse = freshPos ?: return fallbackCornerPosition(windowWidth, windowHeight, horizontalOffset, verticalOffset)
        return calculateWindowPositionFromClick(
            posToUse.x, posToUse.y, posToUse.position,
            windowWidth, windowHeight,
            screenSize.width, screenSize.height,
            horizontalOffset, verticalOffset
        )
    }

    if (getOperatingSystem() == OperatingSystem.MACOS) {
        val (x0, y0) = getStatusItemXYForMac()
        if (x0 != 0 || y0 != 0) {
            TrayClickTracker.setClickPosition(x0, y0, getTrayPosition())
        }
        val pos = TrayClickTracker.getLastClickPosition()
        if (pos != null) {
            return calculateWindowPositionFromClick(
                pos.x, pos.y, pos.position,
                windowWidth, windowHeight,
                screenSize.width, screenSize.height,
                horizontalOffset, verticalOffset
            )
        }
    }

    if (getOperatingSystem() == OperatingSystem.LINUX) {
        val clickPos = TrayClickTracker.getLastClickPosition() ?: loadTrayClickPosition()
        if (clickPos != null) {
            return calculateWindowPositionFromClick(
                clickPos.x, clickPos.y, clickPos.position,
                windowWidth, windowHeight,
                screenSize.width, screenSize.height,
                horizontalOffset, verticalOffset
            )
        }
    }

    return when (getTrayPosition()) {
        TrayPosition.TOP_LEFT -> WindowPosition(x = (0 + horizontalOffset).dp, y = (0 + verticalOffset).dp)
        TrayPosition.TOP_RIGHT -> WindowPosition(
            x = (screenSize.width - windowWidth + horizontalOffset).dp,
            y = (0 + verticalOffset).dp
        )
        TrayPosition.BOTTOM_LEFT -> WindowPosition(
            x = (0 + horizontalOffset).dp,
            y = (screenSize.height - windowHeight + verticalOffset).dp
        )
        TrayPosition.BOTTOM_RIGHT -> WindowPosition(
            x = (screenSize.width - windowWidth + horizontalOffset).dp,
            y = (screenSize.height - windowHeight + verticalOffset).dp
        )
    }
}

/** Variante par instance (Windows multi-tray, mac precise) + offsets */
fun getTrayWindowPositionForInstance(
    instanceId: String,
    windowWidth: Int,
    windowHeight: Int,
    horizontalOffset: Int = 0,
    verticalOffset: Int = 0
): WindowPosition {
    val os = getOperatingSystem()
    val screenSize = Toolkit.getDefaultToolkit().screenSize

    return when (os) {
        OperatingSystem.WINDOWS -> {
            val pos = TrayClickTracker.getLastClickPosition(instanceId)
                ?: return fallbackCornerPosition(windowWidth, windowHeight, horizontalOffset, verticalOffset)
            calculateWindowPositionFromClick(
                pos.x, pos.y, pos.position,
                windowWidth, windowHeight,
                screenSize.width, screenSize.height,
                horizontalOffset, verticalOffset
            )
        }
        OperatingSystem.MACOS -> {
            val trayStruct = MacTrayInitializer.getNativeTrayStruct(instanceId)
            if (trayStruct != null) {
                val xRef = IntByReference()
                val yRef = IntByReference()
                val lib = MacTrayLoader.lib
                val precise = try {
                    lib.tray_get_status_item_position_for(trayStruct, xRef, yRef) != 0
                } catch (_: Throwable) { false }
                val x = xRef.value
                val y = yRef.value
                if (precise) {
                    val regionStr = runCatching { lib.tray_get_status_item_region_for(trayStruct) }.getOrNull()
                    val trayPos = if (regionStr != null) getMacTrayPosition(regionStr)
                    else convertPositionToCorner(x, y, screenSize.width, screenSize.height)
                    TrayClickTracker.setClickPosition(instanceId, x, y, trayPos)
                    return calculateWindowPositionFromClick(
                        x, y, trayPos,
                        windowWidth, windowHeight,
                        screenSize.width, screenSize.height,
                        horizontalOffset, verticalOffset
                    )
                }
            }
            // Fallback global
            getTrayWindowPosition(windowWidth, windowHeight, horizontalOffset, verticalOffset)
        }
        else -> getTrayWindowPosition(windowWidth, windowHeight, horizontalOffset, verticalOffset)
    }
}

/**
 * Calcule la position (x,y) depuis un clic précis + applique les offsets et un clamp aux bords écran.
 */
private fun calculateWindowPositionFromClick(
    clickX: Int, clickY: Int, trayPosition: TrayPosition,
    windowWidth: Int, windowHeight: Int,
    screenWidth: Int, screenHeight: Int,
    horizontalOffset: Int,
    verticalOffset: Int
): WindowPosition {
    var x = clickX - (windowWidth / 2)
    var y = if (trayPosition == TrayPosition.TOP_LEFT || trayPosition == TrayPosition.TOP_RIGHT) {
        clickY
    } else {
        clickY - windowHeight
    }

    // Offsets utilisateur
    x += horizontalOffset
    y += verticalOffset

    // Clamp écran
    if (x < 0) x = 0 else if (x + windowWidth > screenWidth) x = screenWidth - windowWidth
    if (y < 0) y = 0 else if (y + windowHeight > screenHeight) y = screenHeight - windowHeight

    return WindowPosition(x = x.dp, y = y.dp)
}

/** Position de repli coin + offsets */
private fun fallbackCornerPosition(
    w: Int, h: Int,
    horizontalOffset: Int,
    verticalOffset: Int
): WindowPosition {
    val screen = Toolkit.getDefaultToolkit().screenSize
    return when (getTrayPosition()) {
        TrayPosition.TOP_LEFT -> WindowPosition((0 + horizontalOffset).dp, (0 + verticalOffset).dp)
        TrayPosition.TOP_RIGHT -> WindowPosition((screen.width - w + horizontalOffset).dp, (0 + verticalOffset).dp)
        TrayPosition.BOTTOM_LEFT -> WindowPosition((0 + horizontalOffset).dp, (screen.height - h + verticalOffset).dp)
        TrayPosition.BOTTOM_RIGHT -> WindowPosition(
            (screen.width - w + horizontalOffset).dp,
            (screen.height - h + verticalOffset).dp
        )
    }
}

internal fun getMacTrayPosition(nativeResult: String?): TrayPosition = when (nativeResult) {
    "top-left"  -> TrayPosition.TOP_LEFT
    "top-right" -> TrayPosition.TOP_RIGHT
    else        -> TrayPosition.TOP_RIGHT
}

internal fun getStatusItemXYForMac(): Pair<Int, Int> {
    val xRef = IntByReference()
    val yRef = IntByReference()
    val lib = MacTrayLoader.lib
    lib.tray_get_status_item_position(xRef, yRef) // if not precise, returns (0,0)
    return xRef.value to yRef.value
}

fun debugDeleteTrayPropertiesFiles() {
    val files = setOfNotNull(
        trayPropertiesFile(),
        legacyPropertiesFile(),
        oldTmpPropertiesFile(),
        macCachePropertiesFile()
    )
    files.filter(File::exists).forEach { runCatching { it.delete() } }
}

// DPI helpers / hit-test utilities unchanged (kept here for completeness)
private fun dpiAwareHalfIconOffset(): Int {
    return try {
        val dpi = Toolkit.getDefaultToolkit().screenResolution
        val scale = dpi / 96.0
        (15 * scale).roundToInt().coerceAtLeast(0)
    } catch (_: Throwable) {
        15
    }
}

internal fun isPointWithinMacStatusItem(px: Int, py: Int): Boolean {
    if (getOperatingSystem() != OperatingSystem.MACOS) return false
    val (ix, iy) = getStatusItemXYForMac()
    if (ix == 0 && iy == 0) return false
    val dpi = runCatching { Toolkit.getDefaultToolkit().screenResolution }.getOrDefault(96)
    val scale = dpi / 96.0
    val half = (14 * scale).roundToInt().coerceAtLeast(8)
    val left = ix - half
    val right = ix + half
    val top = iy - half
    val bottom = iy + half
    return px in left..right && py in top..bottom
}

internal fun isPointWithinLinuxStatusItem(px: Int, py: Int): Boolean {
    if (getOperatingSystem() != OperatingSystem.LINUX) return false
    val click = TrayClickTracker.getLastClickPosition() ?: loadTrayClickPosition() ?: return false
    val (ix, iy) = click.x to click.y
    val baseIconSizeAt1x = when (detectLinuxDesktopEnvironment()) {
        LinuxDesktopEnvironment.KDE      -> 22
        LinuxDesktopEnvironment.GNOME    -> 24
        LinuxDesktopEnvironment.CINNAMON -> 24
        LinuxDesktopEnvironment.MATE     -> 24
        LinuxDesktopEnvironment.XFCE     -> 24
        else                             -> 24
    }
    val dpi = runCatching { Toolkit.getDefaultToolkit().screenResolution }.getOrDefault(96)
    val scale = (dpi / 96.0).coerceAtLeast(0.5)
    val half = (baseIconSizeAt1x * 0.5 * scale).toInt().coerceAtLeast(8)
    val fudge = (4 * scale).toInt().coerceAtLeast(2)
    val left   = ix - half - fudge
    val right  = ix + half + fudge
    val top    = iy - half - fudge
    val bottom = iy + half + fudge
    return px in left..right && py in top..bottom
}
