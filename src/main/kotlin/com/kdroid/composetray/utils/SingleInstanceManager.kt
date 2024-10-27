package com.kdroid.composetray.utils
import com.kdroid.kmplog.Log
import com.kdroid.kmplog.d
import com.kdroid.kmplog.e
import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.*


/**
 * Singleton object to manage the single instance of an application.
 *
 * This object ensures that only one instance of the application can run at a time,
 * and provides a mechanism to notify the running instance when another instance attempts to start.
 */
object SingleInstanceManager {
    private var fileChannel: FileChannel? = null
    private var fileLock: FileLock? = null
    private val APP_IDENTIFIER = getAppIdentifier()
    private val LOCK_FILE_NAME = "$APP_IDENTIFIER.lock"
    private val MESSAGE_FILE_NAME = "${APP_IDENTIFIER}_restore_request"
    private const val TAG = "SingleInstanceChecker"
    private var isWatching = false
    fun isSingleInstance(onRestoreRequest: () -> Unit): Boolean {
        // If the lock is already acquired by this process, we are the first instance
        if (fileLock != null) {
            Log.d(TAG, "The lock is already held by this process")
            return true
        }
        val lockFile = createLockFile()
        fileChannel = RandomAccessFile(lockFile, "rw").channel
        return try {
            fileLock = fileChannel?.tryLock()
            if (fileLock != null) {
                // We are the only instance
                Log.d(TAG, "Lock acquired, starting to watch for restore requests")
                // Ensure that watching is started only once
                if (!isWatching) {
                    isWatching = true
                    watchForRestoreRequests(onRestoreRequest)
                }
                Runtime.getRuntime().addShutdownHook(Thread {
                    releaseLock()
                    lockFile.delete()
                    deleteRestoreRequestFile()
                    Log.d(TAG, "Shutdown hook executed")
                })
                true
            } else {
                // Another instance is already running
                sendRestoreRequest()
                Log.d(TAG, "Restore request sent to the existing instance")
                false
            }
        } catch (e: OverlappingFileLockException) {
            // The lock is already held by this process
            Log.d(TAG, "The lock is already held by this process (OverlappingFileLockException)")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error in isSingleInstance", e)
            false
        }
    }
    private fun createLockFile(): File {
        val lockFilePath = System.getProperty("java.io.tmpdir") + File.separator + LOCK_FILE_NAME
        return File(lockFilePath)
    }
    private fun watchForRestoreRequests(onRestoreRequest: () -> Unit) {
        Thread {
            try {
                val tmpDir = Paths.get(System.getProperty("java.io.tmpdir"))
                val watchService = FileSystems.getDefault().newWatchService()
                tmpDir.register(watchService, StandardWatchEventKinds.ENTRY_CREATE)
                Log.d(TAG, "Watching directory: $tmpDir for restore requests")
                while (true) {
                    val key = watchService.take()
                    for (event in key.pollEvents()) {
                        val kind = event.kind()
                        if (kind == StandardWatchEventKinds.OVERFLOW) {
                            continue
                        }
                        val filename = event.context() as Path
                        if (filename.toString() == MESSAGE_FILE_NAME) {
                            Log.d(TAG, "Restore request file detected")
                            onRestoreRequest()
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
                Log.e(TAG, "Error in watchForRestoreRequests", e)
            }
        }.start()
    }
    private fun sendRestoreRequest() {
        try {
            val restoreRequestFile = Paths.get(System.getProperty("java.io.tmpdir"), MESSAGE_FILE_NAME)
            Files.createFile(restoreRequestFile)
            Log.d(TAG, "Restore request file created: $restoreRequestFile")
        } catch (e: Exception) {
            Log.e(TAG, "Error while sending restore request", e)
        }
    }
    private fun deleteRestoreRequestFile() {
        try {
            val restoreRequestFile = Paths.get(System.getProperty("java.io.tmpdir"), MESSAGE_FILE_NAME)
            Files.deleteIfExists(restoreRequestFile)
            Log.d(TAG, "Restore request file deleted")
        } catch (e: Exception) {
            Log.e(TAG, "Error while deleting restore request file", e)
        }
    }
    private fun releaseLock() {
        try {
            fileLock?.release()
            fileChannel?.close()
            Log.d(TAG, "Lock released")
        } catch (e: Exception) {
            Log.e(TAG, "Error while releasing the lock", e)
        }
    }
    private fun getAppIdentifier(): String {
        val callerClassName = Thread.currentThread().stackTrace[3].className
        return callerClassName.replace(".", "_")
    }
}