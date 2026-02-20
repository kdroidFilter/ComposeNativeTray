package com.kdroid.composetray.lib.mac

import java.util.function.Consumer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

object MacOSMenuBarThemeDetector {

    private val listeners: MutableSet<Consumer<Boolean>> = ConcurrentHashMap.newKeySet()

    private val callbackExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "MacOS MenuBar Theme Detector Thread").apply { isDaemon = true }
    }

    private val themeChangedCallback = object : MacNativeBridge.ThemeChangeCallback {
        override fun onThemeChanged(isDark: Int) {
            callbackExecutor.execute {
                val dark = isDark != 0
                notifyListeners(dark)
            }
        }
    }

    init {
        MacNativeBridge.nativeSetThemeCallback(themeChangedCallback)
    }

    fun isDark(): Boolean {
        return MacNativeBridge.nativeIsMenuDark() != 0
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
