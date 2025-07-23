package com.kdroid.composetray.tray.impl

import com.kdroid.composetray.lib.linux.libtray.LibTrayDBus
import com.kdroid.composetray.menu.api.TrayMenuBuilder
import com.kdroid.composetray.menu.impl.LinuxDBusTrayMenuBuilderImpl
import com.kdroid.composetray.utils.debugln
import com.kdroid.composetray.utils.errorln
import com.sun.jna.Pointer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Initializer for Linux tray using the DBus-based implementation.
 * This implementation fixes issues with GNOME desktop environments.
 */
object LinuxDBusTrayInitializer {
    private val trayLib: LibTrayDBus = LibTrayDBus.INSTANCE
    private val trayHandle = AtomicReference<Pointer?>(null)
    private val menuBuilder = AtomicReference<LinuxDBusTrayMenuBuilderImpl?>(null)
    private val running = AtomicBoolean(false)
    private val initialized = AtomicBoolean(false)
    private var trayThread: Thread? = null
    private val disposeLatch = AtomicReference<CountDownLatch?>(null)
    
    // Keep references to callbacks to prevent garbage collection
    private val activateCallback = AtomicReference<LibTrayDBus.ActivateCallback?>(null)

    fun dispose() {
        debugln { "LinuxDBusTrayInitializer: Disposing tray..." }

        if (!initialized.get()) {
            debugln { "LinuxDBusTrayInitializer: Not initialized, nothing to dispose" }
            return
        }

        // Create a latch to wait for disposal completion
        val latch = CountDownLatch(1)
        disposeLatch.set(latch)

        // Signal the tray thread to exit
        running.set(false)

        try {
            // Get the current tray handle
            val handle = trayHandle.get()
            
            // Destroy the tray handle if it exists
            if (handle != null) {
                trayLib.destroy_handle(handle)
            }
            
            // Wait for the tray thread to clean up (with timeout)
            if (!latch.await(2, java.util.concurrent.TimeUnit.SECONDS)) {
                errorln { "LinuxDBusTrayInitializer: Timeout waiting for tray disposal" }
                // Force interrupt the thread if it doesn't exit cleanly
                trayThread?.interrupt()
            }
        } catch (e: InterruptedException) {
            errorln { "LinuxDBusTrayInitializer: Interrupted while waiting for disposal: $e" }
        }

        // Clear references
        menuBuilder.get()?.dispose()
        menuBuilder.set(null)
        trayHandle.set(null)
        activateCallback.set(null)
        initialized.set(false)
        trayThread = null
        disposeLatch.set(null)

        // Shutdown the tray system
        trayLib.shutdown_tray_system()

        debugln { "LinuxDBusTrayInitializer: Disposal complete" }
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
            debugln { "LinuxDBusTrayInitializer: Already initialized, disposing previous instance" }
            dispose()
            // Give the system more time to clean up completely
            Thread.sleep(500)
        }

        // Create a new thread for the tray to ensure operations happen on the same thread
        trayThread = Thread {
            try {
                debugln { "LinuxDBusTrayInitializer: Initializing tray on dedicated thread" }

                // Initialize the tray system
                val initResult = trayLib.init_tray_system()
                if (initResult != 0) {
                    errorln { "LinuxDBusTrayInitializer: Failed to initialize tray system: $initResult" }
                    initialized.set(false)
                    return@Thread
                }

                // Create the tray
                val handle = trayLib.create_tray("ComposeNativeTray")
                if (handle == null) {
                    errorln { "LinuxDBusTrayInitializer: Failed to create tray" }
                    initialized.set(false)
                    return@Thread
                }
                
                trayHandle.set(handle)
                
                // Set tray properties
                trayLib.set_icon_by_path(handle, iconPath)
                trayLib.set_tooltip_title(handle, tooltip)
                
                // Set primary action callback if provided
                if (primaryAction != null) {
                    val callback = object : LibTrayDBus.ActivateCallback {
                        override fun invoke(x: Int, y: Int, userData: Pointer?) {
                            primaryAction.invoke()
                        }
                    }
                    activateCallback.set(callback)
                    trayLib.set_activate_callback(handle, callback, null)
                }
                
                // Build menu if content is provided
                if (menuContent != null) {
                    val builder = LinuxDBusTrayMenuBuilderImpl(iconPath, tooltip, primaryAction)
                    menuContent.invoke(builder)
                    
                    val menuHandle = builder.getMenuHandle()
                    if (menuHandle != null) {
                        trayLib.set_context_menu(handle, menuHandle)
                    }
                    
                    menuBuilder.set(builder)
                }
                
                running.set(true)
                initialized.set(true)
                
                debugln { "LinuxDBusTrayInitializer: Tray initialized successfully, starting event loop" }
                
                // Run the event loop
                while (running.get()) {
                    trayLib.sni_process_events()
                    Thread.sleep(100) // Add a small delay to prevent high CPU usage
                }
                
                debugln { "LinuxDBusTrayInitializer: Event loop ended" }
                
            } catch (e: Exception) {
                errorln { "LinuxDBusTrayInitializer: Error in tray thread: $e" }
            } finally {
                running.set(false)
                initialized.set(false)
                
                // Signal that disposal is complete
                disposeLatch.get()?.countDown()
            }
        }.apply {
            name = "LinuxDBusTray-Thread"
            isDaemon = false
            start()
        }
        
        // Wait a bit to ensure the tray is initialized
        Thread.sleep(200)
    }
    
    fun update(
        iconPath: String,
        tooltip: String,
        primaryAction: (() -> Unit)?,
        primaryActionLabel: String,
        menuContent: (TrayMenuBuilder.() -> Unit)?
    ) {
        if (!initialized.get()) {
            initialize(iconPath, tooltip, primaryAction, primaryActionLabel, menuContent)
            return
        }
        
        val handle = trayHandle.get() ?: return
        
        // Update tray properties
        trayLib.update_icon_by_path(handle, iconPath)
        trayLib.set_tooltip_title(handle, tooltip)
        
        // Update primary action callback if provided
        if (primaryAction != null) {
            val callback = object : LibTrayDBus.ActivateCallback {
                override fun invoke(x: Int, y: Int, userData: Pointer?) {
                    primaryAction.invoke()
                }
            }
            activateCallback.set(callback)
            trayLib.set_activate_callback(handle, callback, null)
        }
        
        // Update menu if content is provided
        if (menuContent != null) {
            val builder = LinuxDBusTrayMenuBuilderImpl(iconPath, tooltip, primaryAction)
            menuContent.invoke(builder)
            
            val menuHandle = builder.getMenuHandle()
            if (menuHandle != null) {
                trayLib.set_context_menu(handle, menuHandle)
            }
            
            // Dispose the old menu builder
            menuBuilder.get()?.dispose()
            menuBuilder.set(builder)
        }
    }
}