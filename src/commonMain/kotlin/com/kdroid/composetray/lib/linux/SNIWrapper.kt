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
         * 1) System property override with absolute path:
         *    -Dcomposetray.native.lib=/abs/path/libtray.so
         * 2) System property pointing to a directory containing the library:
         *    -Dcomposetray.native.lib.path=/abs/dir  (expects libtray.so inside)
         * 3) System.loadLibrary("tray") so that external extractors (e.g. Conveyor
         *    app.jvm.extract-native-libraries=true) can provide the library in the
         *    JVM library path.
         * 4) Fallback to JNA resource/classpath discovery Native.load("tray").
         */
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

        private fun tryLoadViaSystemLibrary(): Boolean = try {
            System.loadLibrary("tray")
            true
        } catch (_: Throwable) { false }

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

        private fun preloadLinuxDependencies() {
            if (!isLinux()) return
            val tmpBase = Files.createTempDirectory("composetray-natives-")
            // Attempt to extract versioned first, then soname link
            val base = "${archFolder()}"
            val candidates = listOf(
                "$base/libdbusmenu-qt5.so.2.6.0",
                "$base/libdbusmenu-qt5.so.2"
            )
            var loaded = false
            for (res in candidates) {
                val p = extractResourceToDir(res, tmpBase)
                if (p != null && p.toFile().exists()) {
                    try {
                        System.load(p.toAbsolutePath().toString())
                        loaded = true
                        break
                    } catch (_: Throwable) {
                        // try next
                    }
                }
            }
            if (!loaded) {
                // If extraction failed or not packaged, continue without preloading
            } else {
                // Make JNA search path include temp dir so that transitive loads can find siblings
                try {
                    NativeLibrary.addSearchPath("tray", tmpBase.toAbsolutePath().toString())
                } catch (_: Throwable) {
                    // ignore
                }
            }
        }

        val INSTANCE: SNIWrapper = run {
            // Preload Linux-only dependencies bundled in resources
            preloadLinuxDependencies()

            // 1) Property overrides
            if (!tryLoadViaSystemProperties()) {
                // 2) System library path
                tryLoadViaSystemLibrary()
            }
            // 3) Always let JNA resolve as well (will succeed if already loaded)
            Native.load("tray", SNIWrapper::class.java) as SNIWrapper
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
    fun add_checkable_menu_action(menu_handle: Pointer?, text: String?, checked: Int, cb: ActionCallback?, data: Pointer?): Pointer?    fun add_menu_separator(menu_handle: Pointer?)
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
