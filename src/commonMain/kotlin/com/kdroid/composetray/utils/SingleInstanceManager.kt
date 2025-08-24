package com.kdroid.composetray.utils

import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.nio.file.StandardWatchEventKinds


/**
 * Singleton object to manage the single instance of an application.
 *
 * This object ensures that only one instance of the application can run at a time,
 * and provides a mechanism to notify the running instance when another instance attempts to start.
 */
object SingleInstanceManager {

    private const val TAG = "SingleInstanceChecker"

    /**
     * Don't inline to [Configuration] initializer to prevent multiple calls with the different stack depth.
     */
    private val APP_IDENTIFIER = getAppIdentifier()

    /**
     * Configuration for a locking mechanism.
     *
     * @property lockFilesDir The directory where lock files will be stored. Defaults to the system's temporary directory.
     * @property lockIdentifier The lock identifier that will be used for generating lock files names.
     */
    data class Configuration(
        val lockFilesDir: Path = Paths.get(System.getProperty("java.io.tmpdir")),
        val lockIdentifier: String = APP_IDENTIFIER
    ) {
        val lockFileName: String = "$lockIdentifier.lock"
        val restoreRequestFileName: String = "$lockIdentifier.restore_request"

        val lockFilePath: Path = lockFilesDir.resolve(lockFileName)
        val restoreRequestFilePath: Path = lockFilesDir.resolve(restoreRequestFileName)
    }

    var configuration: Configuration = Configuration()
        set(value) {
            check(fileChannel == null) { "Configuration can be changed only before first call to isSingleInstance()!" }
            field = value
        }

    private var fileChannel: FileChannel? = null
    private var fileLock: FileLock? = null
    private var isWatching = false

    /**
     * Checks if the current process is the single running instance.
     *
     * @param onRestoreRequest A function to be executed if a restore request is received from another instance.
     */
    fun isSingleInstance(onRestoreFileCreated: (Path.() -> Unit)? = null, onRestoreRequest: Path.() -> Unit): Boolean {
        // If the lock is already acquired by this process, we are the first instance
        if (fileLock != null) {
            debugln { "$TAG: The lock is already held by this process" }
            return true
        }
        val lockFile = createLockFile()
        fileChannel = RandomAccessFile(lockFile, "rw").channel
        return try {
            fileLock = fileChannel?.tryLock()
            if (fileLock != null) {
                // We are the only instance
                debugln { "$TAG: Lock acquired, starting to watch for restore requests" }
                // Ensure that watching is started only once
                if (!isWatching) {
                    isWatching = true
                    watchForRestoreRequests(onRestoreRequest)
                }
                Runtime.getRuntime().addShutdownHook(Thread {
                    releaseLock()
                    lockFile.delete()
                    deleteRestoreRequestFile()
                    debugln { "$TAG: Shutdown hook executed" }
                })
                true
            } else {
                // Another instance is already running
                sendRestoreRequest(onRestoreFileCreated)
                debugln { "$TAG: Restore request sent to the existing instance" }
                false
            }
        } catch (e: OverlappingFileLockException) {
            // The lock is already held by this process
            debugln { "$TAG: The lock is already held by this process (OverlappingFileLockException)" }
            return true
        } catch (e: Exception) {
            errorln { "$TAG: Error in isSingleInstance: $e" }
            false
        }
    }

    private fun createLockFile(): File {
        val lockFile = configuration.lockFilePath.toFile()
        lockFile.parentFile.mkdirs()
        return lockFile
    }

    private fun watchForRestoreRequests(onRestoreRequest: Path.() -> Unit) {
        Thread {
            try {
                val watchService = FileSystems.getDefault().newWatchService()
                configuration.lockFilesDir.register(watchService, StandardWatchEventKinds.ENTRY_CREATE)
                debugln { "$TAG: Watching directory: ${configuration.lockFilesDir} for restore requests" }
                while (true) {
                    val key = watchService.take()
                    for (event in key.pollEvents()) {
                        val kind = event.kind()
                        if (kind == StandardWatchEventKinds.OVERFLOW) {
                            continue
                        }
                        val filename = event.context() as Path
                        if (filename.toString() == configuration.restoreRequestFileName) {
                            debugln { "$TAG: Restore request file detected" }
                            configuration.restoreRequestFilePath.onRestoreRequest()
                            // Remove the request file after processing
                            deleteRestoreRequestFile()
                        }
                    }
                    val valid = key.reset()
                    if (!valid) {
                        break
                    }
                }
            } catch (e: Exception) {
                errorln { "$TAG: Error in watchForRestoreRequests: $e" }
            }
        }.start()
    }

    private fun sendRestoreRequest(onRestoreFileCreated: (Path.() -> Unit)?) {
        try {
            val restoreRequestFilePath = configuration.restoreRequestFilePath
            if (onRestoreFileCreated != null) {
                val tempRestoreFilePath = Files.createTempFile(configuration.lockIdentifier, ".restore_request")
                tempRestoreFilePath.onRestoreFileCreated()
                Files.move(tempRestoreFilePath, restoreRequestFilePath, StandardCopyOption.REPLACE_EXISTING)
            } else {
                Files.createFile(restoreRequestFilePath)
            }
            debugln { "$TAG: Restore request file created: $restoreRequestFilePath" }
        } catch (e: Exception) {
            errorln { "$TAG: Error while sending restore request: $e" }
        }
    }

    private fun deleteRestoreRequestFile() {
        try {
            val restoreRequestFilePath = configuration.restoreRequestFilePath
            Files.deleteIfExists(restoreRequestFilePath)
            debugln { "$TAG: Restore request file deleted: $restoreRequestFilePath" }
        } catch (e: Exception) {
            errorln { "$TAG: Error while deleting restore request file: $e" }
        }
    }

    private fun releaseLock() {
        try {
            fileLock?.release()
            fileChannel?.close()
            debugln { "$TAG: Lock released" }
        } catch (e: Exception) {
            errorln { "$TAG: Error while releasing the lock: $e" }
        }
    }

    private fun getAppIdentifier(): String {
        // Use unified app ID provider to avoid cross-app conflicts and allow explicit override
        return AppIdProvider.appId()
    }
}