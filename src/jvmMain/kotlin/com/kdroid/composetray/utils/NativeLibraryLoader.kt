package com.kdroid.composetray.utils

import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Extracts a native library from the classpath (composetray/native/<platform>/)
 * to a temporary directory and registers it with JNA.
 *
 * This is needed because JNA's default classpath search doesn't look inside
 * the composetray/native/ namespace prefix.
 */
internal object NativeLibraryLoader {

    private val tempDir: File by lazy {
        Files.createTempDirectory("composetray-natives").toFile().apply { deleteOnExit() }
    }

    /**
     * Extracts the native library from the classpath and registers it with JNA.
     *
     * @param libraryName the library name (e.g. "systray" or "tray")
     * @param caller a class from the same classloader to use for resource lookup
     */
    fun extractAndRegister(libraryName: String, caller: Class<*>) {
        val platform = detectPlatform()
        val fileName = mapLibraryName(libraryName)
        val resourcePath = "composetray/native/$platform/$fileName"

        val url = caller.classLoader?.getResource(resourcePath)
        if (url != null) {
            val targetFile = File(tempDir, fileName)
            if (!targetFile.exists()) {
                url.openStream().use { input ->
                    Files.copy(input, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
                targetFile.deleteOnExit()
            }
            // Add the temp directory to JNA's search path
            NativeLibrary.addSearchPath(libraryName, tempDir.absolutePath)
        }

        Native.register(caller, libraryName)
    }

    private fun detectPlatform(): String {
        val os = System.getProperty("os.name")?.lowercase() ?: ""
        val arch = System.getProperty("os.arch") ?: ""
        return when {
            os.contains("win") -> when {
                arch.contains("aarch64") || arch.contains("arm") -> "win32-arm64"
                else -> "win32-x86-64"
            }
            os.contains("linux") -> when {
                arch.contains("aarch64") || arch.contains("arm") -> "linux-aarch64"
                else -> "linux-x86-64"
            }
            os.contains("mac") -> when {
                arch.contains("aarch64") || arch.contains("arm") -> "darwin-aarch64"
                else -> "darwin-x86-64"
            }
            else -> "unknown"
        }
    }

    private fun mapLibraryName(name: String): String {
        val os = System.getProperty("os.name")?.lowercase() ?: ""
        return when {
            os.contains("win") -> "$name.dll"
            os.contains("mac") -> "lib$name.dylib"
            else -> "lib$name.so"
        }
    }
}
