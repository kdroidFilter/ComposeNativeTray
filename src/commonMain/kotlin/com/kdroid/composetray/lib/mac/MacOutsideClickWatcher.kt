package com.kdroid.composetray.lib.mac

import com.kdroid.composetray.utils.isPointWithinMacStatusItem
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import io.github.kdroidfilter.platformtools.OperatingSystem
import io.github.kdroidfilter.platformtools.getOperatingSystem
import java.awt.MouseInfo
import java.awt.Window
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

interface ApplicationServices : Library {
    companion object {
        val INSTANCE: ApplicationServices =
            Native.load("ApplicationServices", ApplicationServices::class.java)
    }

    fun CGEventCreate(source: Pointer?): Pointer // CGEventRef
    fun CGEventGetLocation(event: Pointer): CGPoint.ByValue  // <-- ByValue is crucial
    fun CFRelease(ref: Pointer)

    fun CGEventSourceButtonState(stateID: Int, button: Int): Boolean
}

// CGPoint as ByValue (two doubles)
open class CGPoint : Structure() {
    @JvmField var x: Double = 0.0
    @JvmField var y: Double = 0.0
    override fun getFieldOrder() = listOf("x", "y")
    class ByValue : CGPoint(), Structure.ByValue
}


/**
 * MacOutsideClickWatcher: encapsulates macOS-specific logic to detect a left-click
 * outside the provided window and invoke a callback to hide it. It also ignores
 * clicks on the macOS status bar tray icon (status item) so that clicking the tray icon
 * does not spuriously hide the window.
 */
class MacOutsideClickWatcher(
    private val windowSupplier: () -> Window?,
    private val onOutsideClick: () -> Unit
) : AutoCloseable {

    private var scheduler: ScheduledExecutorService? = null
    private var prevLeft = false

    fun start() {
        if (getOperatingSystem() != OperatingSystem.MACOS) return
        if (scheduler != null) return
        scheduler = Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "MacOutsideClickWatcher").apply { isDaemon = true }
        }.also { exec ->
            exec.scheduleAtFixedRate({ pollOnce() }, 0, 16, TimeUnit.MILLISECONDS)
        }
    }

    private fun pollOnce() {
        try {
            val asvc = ApplicationServices.INSTANCE
            val left = asvc.CGEventSourceButtonState(0, 0) // kCGEventSourceStateCombinedSessionState, BUTTON_LEFT

            if (left && left != prevLeft) {
                val win = windowSupplier.invoke()
                if (win != null && win.isShowing) {
                    val pointer = try { MouseInfo.getPointerInfo() } catch (_: Throwable) { null }
                    val loc = pointer?.location
                    if (loc != null) {
                        val px = loc.x
                        val py = loc.y
                        val winLoc = try { win.locationOnScreen } catch (_: Throwable) { null }
                        if (winLoc != null) {
                            val wx = winLoc.x
                            val wy = winLoc.y
                            val ww = win.width
                            val wh = win.height
                            val insideWindow = px >= wx && px < wx + ww && py >= wy && py < wy + wh
                            val onTrayIcon = isPointWithinMacStatusItem(px, py)
                            if (!insideWindow && !onTrayIcon) {
                                onOutsideClick.invoke()
                            }
                        }
                    }
                }
            }
            prevLeft = left
        } catch (_: Throwable) {
            // Swallow errors to avoid crashing the scheduler
        }
    }

    fun stop() = close()

    override fun close() {
        try { scheduler?.shutdownNow() } catch (_: Throwable) {}
        scheduler = null
    }
}