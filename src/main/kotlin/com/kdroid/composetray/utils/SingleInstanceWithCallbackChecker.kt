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

object SingleInstanceWithCallbackChecker {
    private var fileChannel: FileChannel? = null
    private var fileLock: FileLock? = null
    private val APP_IDENTIFIER = getAppIdentifier()
    private val LOCK_FILE_NAME = "$APP_IDENTIFIER.lock"
    private val MESSAGE_FILE_NAME = "${APP_IDENTIFIER}_restore_request"
    private const val TAG = "SingleInstanceChecker"
    private var isWatching = false

    fun isSingleInstance(onRestoreRequest: () -> Unit): Boolean {
        // Si le verrou est déjà acquis par ce processus, nous sommes la première instance
        if (fileLock != null) {
            Log.d(TAG, "Le verrou est déjà détenu par ce processus")
            return true
        }

        val lockFile = createLockFile()
        fileChannel = RandomAccessFile(lockFile, "rw").channel

        return try {
            fileLock = fileChannel?.tryLock()
            if (fileLock != null) {
                // Nous sommes la seule instance
                Log.d(TAG, "Verrou obtenu, démarrage de la surveillance des demandes de restauration")
                // Assurez-vous que la surveillance n'est démarrée qu'une seule fois
                if (!isWatching) {
                    isWatching = true
                    watchForRestoreRequests(onRestoreRequest)
                }
                Runtime.getRuntime().addShutdownHook(Thread {
                    releaseLock()
                    lockFile.delete()
                    deleteRestoreRequestFile()
                    Log.d(TAG, "Shutdown hook exécuté")
                })
                true
            } else {
                // Une autre instance est déjà en cours d'exécution
                sendRestoreRequest()
                Log.d(TAG, "Demande de restauration envoyée à l'instance existante")
                false
            }
        } catch (e: OverlappingFileLockException) {
            // Le verrou est déjà détenu par ce processus
            Log.d(TAG, "Le verrou est déjà détenu par ce processus (OverlappingFileLockException)")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Erreur dans isSingleInstance", e)
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

                Log.d(TAG, "Surveillance du répertoire: $tmpDir pour les demandes de restauration")

                while (true) {
                    val key = watchService.take()
                    for (event in key.pollEvents()) {
                        val kind = event.kind()
                        if (kind == StandardWatchEventKinds.OVERFLOW) {
                            continue
                        }

                        val filename = event.context() as Path
                        if (filename.toString() == MESSAGE_FILE_NAME) {
                            Log.d(TAG, "Fichier de demande de restauration détecté")
                            onRestoreRequest()
                            // Supprimer le fichier de demande après le traitement
                            deleteRestoreRequestFile()
                        }
                    }
                    val valid = key.reset()
                    if (!valid) {
                        break
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erreur dans watchForRestoreRequests", e)
            }
        }.start()
    }

    private fun sendRestoreRequest() {
        try {
            val restoreRequestFile = Paths.get(System.getProperty("java.io.tmpdir"), MESSAGE_FILE_NAME)
            Files.createFile(restoreRequestFile)
            Log.d(TAG, "Fichier de demande de restauration créé: $restoreRequestFile")
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors de l'envoi de la demande de restauration", e)
        }
    }

    private fun deleteRestoreRequestFile() {
        try {
            val restoreRequestFile = Paths.get(System.getProperty("java.io.tmpdir"), MESSAGE_FILE_NAME)
            Files.deleteIfExists(restoreRequestFile)
            Log.d(TAG, "Fichier de demande de restauration supprimé")
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors de la suppression du fichier de demande de restauration", e)
        }
    }

    private fun releaseLock() {
        try {
            fileLock?.release()
            fileChannel?.close()
            Log.d(TAG, "Verrou relâché")
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors du relâchement du verrou", e)
        }
    }

    private fun getAppIdentifier(): String {
        val callerClassName = Thread.currentThread().stackTrace[3].className
        return callerClassName.replace(".", "_")
    }
}
