package com.kdroid.composetray.lib.mac

import co.touchlab.kermit.Logger
import com.sun.jna.Native
import java.util.function.Consumer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import com.kdroid.composetray.lib.mac.MacTrayManager.MacTrayLibrary

private val logger = Logger.withTag("MacOSMenuBarThemeDetector")

object MacOSMenuBarThemeDetector {

    private val trayLib: MacTrayLibrary = Native.load("MacTray", MacTrayLibrary::class.java)

    private val listeners: MutableSet<Consumer<Boolean>> = ConcurrentHashMap.newKeySet()

    private val callbackExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "MacOS MenuBar Theme Detector Thread").apply { isDaemon = true }
    }

    private val themeChangedCallback = object : MacTrayManager.ThemeCallback {
        override fun invoke(isDark: Int) {
            callbackExecutor.execute {
                val dark = isDark != 0
                notifyListeners(dark)
            }
        }
    }

    init {
        trayLib.tray_set_theme_callback(themeChangedCallback)
    }

    fun isDark(): Boolean {
        return trayLib.tray_is_menu_dark() != 0
    }

    fun registerListener(listener: Consumer<Boolean>) {
        listeners.add(listener)
        // Notify with current state upon registration
        listener.accept(isDark())
    }

    fun removeListener(listener: Consumer<Boolean>) {
        listeners.remove(listener)
    }

    private fun notifyListeners(isDark: Boolean) {
        listeners.forEach { it.accept(isDark) }
    }
}