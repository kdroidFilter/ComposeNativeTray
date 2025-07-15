package com.kdroid.composetray.tray.impl

import com.kdroid.composetray.lib.linux.libtray.LibTray
import com.kdroid.composetray.lib.linux.libtray.LinuxNativeTray
import com.kdroid.composetray.menu.api.TrayMenuBuilder
import com.kdroid.composetray.menu.impl.LinuxLibTrayMenuBuilderImpl
import com.kdroid.kmplog.Log
import com.kdroid.kmplog.d
import com.kdroid.kmplog.e
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

object LinuxTrayInitializer {
    private val trayLib: LibTray = LibTray.INSTANCE
    private val tray = AtomicReference<LinuxNativeTray?>(null)
    private val menuBuilder = AtomicReference<LinuxLibTrayMenuBuilderImpl?>(null)
    private val running = AtomicBoolean(false)
    private val initialized = AtomicBoolean(false)
    private var trayThread: Thread? = null
    private val disposeLatch = AtomicReference<CountDownLatch?>(null)

    fun dispose() {
        Log.d("LinuxTrayInitializer", "Disposing tray...")

        if (!initialized.get()) {
            Log.d("LinuxTrayInitializer", "Not initialized, nothing to dispose")
            return
        }

        // Create a latch to wait for disposal completion
        val latch = CountDownLatch(1)
        disposeLatch.set(latch)

        // Signal the tray thread to exit
        running.set(false)

        try {
            // Call tray_exit() to signal Qt to quit
            // This must be called BEFORE waiting for the thread
            trayLib.tray_exit()

            // Wait for the tray thread to clean up (with timeout)
            if (!latch.await(2, java.util.concurrent.TimeUnit.SECONDS)) {
                Log.e("LinuxTrayInitializer", "Timeout waiting for tray disposal")
                // Force interrupt the thread if it doesn't exit cleanly
                trayThread?.interrupt()
            }
        } catch (e: InterruptedException) {
            Log.e("LinuxTrayInitializer", "Interrupted while waiting for disposal", e)
        }

        // Clear references
        menuBuilder.get()?.dispose()
        menuBuilder.set(null)
        tray.set(null)
        initialized.set(false)
        trayThread = null
        disposeLatch.set(null)

        Log.d("LinuxTrayInitializer", "Disposal complete")
    }

    fun initialize(
        iconPath: String,
        tooltip: String,
        primaryAction: (() -> Unit)?,
        primaryActionLabel: String,
        menuContent: (TrayMenuBuilder.() -> Unit)?
    ) {
        // Dispose any existing instance
        if (initialized.get()) {
            Log.d("LinuxTrayInitializer", "Already initialized, disposing previous instance")
            dispose()
            // Give Qt more time to clean up completely
            Thread.sleep(500)
        }

        // Create the tray structure
        val linuxTray = LinuxNativeTray().apply {
            icon_filepath = iconPath
            this.tooltip = tooltip

            // Set primary action callback if provided
            primaryAction?.let {
                cb = LinuxNativeTray.TrayCallback { _ ->
                    it.invoke()
                }
            }
        }

        // Build menu if content is provided
        if (menuContent != null) {
            val builder = LinuxLibTrayMenuBuilderImpl(iconPath, tooltip, primaryAction)
            menuContent.invoke(builder)
            linuxTray.menu = builder.build()
            menuBuilder.set(builder)
        }

        tray.set(linuxTray)
        running.set(true)
        initialized.set(true)

        // Create a new thread for the tray to ensure Qt operations happen on the same thread
        trayThread = Thread {
            try {
                Log.d("LinuxTrayInitializer", "Initializing tray on dedicated thread")

                // Initialize the tray
                val result = trayLib.tray_init(linuxTray)
                if (result != 0) {
                    Log.e("LinuxTrayInitializer", "Failed to initialize tray: $result")
                    initialized.set(false)
                    return@Thread
                }

                Log.d("LinuxTrayInitializer", "Tray initialized successfully, starting event loop")

                // Run the event loop
                while (running.get()) {
                    val loopResult = trayLib.tray_loop(1) // Blocking call
                    if (loopResult != 0) {
                        Log.d("LinuxTrayInitializer", "Tray loop exited with result: $loopResult")
                        break
                    }
                }

                Log.d("LinuxTrayInitializer", "Event loop ended")

            } catch (e: Exception) {
                Log.e("LinuxTrayInitializer", "Error in tray thread", e)
            } finally {
                running.set(false)
                initialized.set(false)

                // Signal that disposal is complete
                disposeLatch.get()?.countDown()
            }
        }.apply {
            name = "LinuxTray-Thread"
            isDaemon = false
            start()
        }

        // Wait a bit to ensure the tray is initialized
        Thread.sleep(200)
    }
}