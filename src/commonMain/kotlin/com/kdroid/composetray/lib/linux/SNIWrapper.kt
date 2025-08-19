package com.kdroid.composetray.lib.linux

import com.sun.jna.*
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

// JNA Interface for sni_wrapper library
interface SNIWrapper : Library {
    companion object {
        /**
         * Robust native library loading strategy to work with packagers like Conveyor.
         *
         * Resolution order (Linux):
         * 1) System.loadLibrary("tray") - FIRST to support external extractors like Conveyor
         *    (app.jvm.extract-native-libraries=true) that provide the library in JVM library path
         * 2) System property override with absolute path:
         *    -Dcomposetray.native.lib=/abs/path/libtray.so
         * 3) System property pointing to a directory containing the library:
         *    -Dcomposetray.native.lib.path=/abs/dir  (expects libtray.so inside)
         * 4) Fallback to JNA resource/classpath discovery Native.load("tray")
         */
        private fun tryLoadViaSystemLibrary(): Boolean = try {
            System.loadLibrary("tray")
            true
        } catch (_: Throwable) {
            false
        }

        private fun tryLoadViaSystemProperties(): Boolean {
            val explicitFile = System.getProperty("composetray.native.lib")?.trim()?.takeIf { it.isNotEmpty() }
            if (!explicitFile.isNullOrEmpty()) {
                val f = File(explicitFile)
                if (f.isFile && f.canRead()) {
                    try {
                        System.load(f.absolutePath)
                        return true
                    } catch (_: Throwable) {
                        // continue to other strategies
                    }
                }
            }
            val dir = System.getProperty("composetray.native.lib.path")?.trim()?.takeIf { it.isNotEmpty() }
            if (!dir.isNullOrEmpty()) {
                val candidate = File(dir, System.mapLibraryName("tray"))
                if (candidate.isFile && candidate.canRead()) {
                    try {
                        System.load(candidate.absolutePath)
                        return true
                    } catch (_: Throwable) {
                        // continue
                    }
                }
            }
            return false
        }

        private fun isLinux(): Boolean = System.getProperty("os.name")?.lowercase()?.contains("linux") == true

        private fun archFolder(): String {
            val arch = System.getProperty("os.arch")?.lowercase() ?: ""
            return when {
                arch.contains("x86_64") || arch.contains("amd64") -> "linux-x86-64"
                else -> "linux-x86-64" // default for now; extend when adding more
            }
        }

        private fun extractResourceToDir(resourcePath: String, targetDir: Path): Path? {
            val url = SNIWrapper::class.java.classLoader.getResource(resourcePath) ?: return null
            val fileName = File(resourcePath).name
            val target = targetDir.resolve(fileName)
            try {
                url.openStream().use { input ->
                    Files.createDirectories(targetDir)
                    Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING)
                }
                // ensure readable/executable for loader
                target.toFile().setReadable(true, false)
                target.toFile().setExecutable(true, false)
                return target
            } catch (_: IOException) {
                return null
            }
        }

        val INSTANCE: SNIWrapper = run {
            var loaded = false

            // 1) FIRST try System.loadLibrary - this supports Conveyor and standard JVM library paths
            if (tryLoadViaSystemLibrary()) {
                loaded = true
                println("Loaded libtray via System.loadLibrary")
            }

            // 2) If not loaded, try property overrides
            if (!loaded && tryLoadViaSystemProperties()) {
                loaded = true
                println("Loaded libtray via system properties")
            }

            // 3) Always call JNA's Native.load as final step
            // If library was already loaded above, JNA will detect this and just create the proxy
            // If not loaded yet, JNA will try to load from resources/classpath
            try {
                Native.load("tray", SNIWrapper::class.java) as SNIWrapper
            } catch (e: UnsatisfiedLinkError) {
                // If we thought we loaded it but JNA can't create proxy, provide helpful error
                if (loaded) {
                    throw UnsatisfiedLinkError(
                        "Library was loaded via System.loadLibrary or properties but JNA couldn't create proxy. " +
                                "This might indicate a version mismatch or architecture incompatibility: ${e.message}"
                    )
                } else {
                    throw e
                }
            }
        }
    }

    // Callback interfaces
    interface ActivateCallback : Callback {
        fun invoke(x: Int, y: Int, data: Pointer?)
    }

    interface SecondaryActivateCallback : Callback {
        fun invoke(x: Int, y: Int, data: Pointer?)
    }

    interface ScrollCallback : Callback {
        fun invoke(delta: Int, orientation: Int, data: Pointer?)
    }

    interface ActionCallback : Callback {
        fun invoke(data: Pointer?)
    }

    // System tray initialization and cleanup
    fun init_tray_system(): Int
    fun shutdown_tray_system()

    // Tray creation and destruction
    fun create_tray(id: String?): Pointer?
    fun destroy_handle(handle: Pointer?)

    // Tray property setters
    fun set_title(handle: Pointer?, title: String?)
    fun set_status(handle: Pointer?, status: String?)
    fun set_icon_by_name(handle: Pointer?, name: String?)
    fun set_icon_by_path(handle: Pointer?, path: String?)
    fun update_icon_by_path(handle: Pointer?, path: String?)
    fun set_tooltip_title(handle: Pointer?, title: String?)
    fun set_tooltip_subtitle(handle: Pointer?, subTitle: String?)

    // Menu creation and management
    fun create_menu(): Pointer?
    fun destroy_menu(menu_handle: Pointer?)
    fun set_context_menu(handle: Pointer?, menu: Pointer?)
    fun add_menu_action(menu_handle: Pointer?, text: String?, cb: ActionCallback?, data: Pointer?): Pointer?
    fun add_disabled_menu_action(menu_handle: Pointer?, text: String?, cb: ActionCallback?, data: Pointer?): Pointer?
    fun add_checkable_menu_action(menu_handle: Pointer?, text: String?, checked: Int, cb: ActionCallback?, data: Pointer?): Pointer?
    fun add_menu_separator(menu_handle: Pointer?)
    fun create_submenu(menu_handle: Pointer?, text: String?): Pointer?
    fun set_menu_item_text(menu_item_handle: Pointer?, text: String?)
    fun set_menu_item_enabled(menu_item_handle: Pointer?, enabled: Int)
    fun set_menu_item_checked(menu_item_handle: Pointer?, checked: Int): Int
    fun remove_menu_item(menu_handle: Pointer?, menu_item_handle: Pointer?)
    fun set_menu_item_icon(menu_item_handle: Pointer?, icon_path_or_name: String?)
    fun set_submenu_icon(submenu_handle: Pointer?, icon_path_or_name: String?)
    fun tray_update(handle: Pointer?)

    fun clear_menu(menu_handle: Pointer?)

    // Tray event callbacks
    fun set_activate_callback(handle: Pointer?, cb: ActivateCallback?, data: Pointer?)
    fun set_secondary_activate_callback(handle: Pointer?, cb: SecondaryActivateCallback?, data: Pointer?)
    fun set_scroll_callback(handle: Pointer?, cb: ScrollCallback?, data: Pointer?)

    // Notifications
    fun show_notification(handle: Pointer?, title: String?, msg: String?, iconName: String?, secs: Int)

    // Event loop management
    fun sni_exec(): Int
    fun sni_process_events()
    fun sni_stop_exec()

    //Debug mode management
    fun sni_set_debug_mode(enabled: Int)
}