package com.kdroid.composetray.utils

import java.io.*
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.file.Files
import java.nio.file.Paths

object SingleInstanceWithCallbackChecker {
    private var fileChannel: FileChannel? = null
    private var fileLock: FileLock? = null
    private const val PIPE_NAME = "single_instance_pipe"

    fun isSingleInstance(onRestoreRequest: () -> Unit): Boolean {
        val lockFile = createLockFile()
        fileChannel = lockFile.outputStream().channel

        return try {
            fileLock = fileChannel?.tryLock()
            if (fileLock != null) {
                // Créer le pipe pour écouter les demandes de restauration
                createPipeAndListen(onRestoreRequest)
                Runtime.getRuntime().addShutdownHook(Thread {
                    releaseLock()
                    lockFile.delete()
                })
                true
            } else {
                // Si une instance est déjà en cours, envoyer une demande de restauration
                sendRestoreRequest()
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun createLockFile(): File {
        val callerClassName = Thread.currentThread().stackTrace[2].className
        val lockFileName = "${callerClassName.replace(".", "_")}.lock"
        val lockFilePath = System.getProperty("java.io.tmpdir") + lockFileName
        return File(lockFilePath)
    }

    private fun createPipeAndListen(onRestoreRequest: () -> Unit) {
        val pipePath = Paths.get(System.getProperty("java.io.tmpdir"), PIPE_NAME)
        if (Files.exists(pipePath)) Files.delete(pipePath)
        Files.createFile(pipePath)

        Thread {
            try {
                BufferedReader(InputStreamReader(Files.newInputStream(pipePath))).use { reader ->
                    while (true) {
                        val line = reader.readLine()
                        if (line == "RESTORE") {
                            onRestoreRequest() // Exécuter le callback de restauration
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    private fun sendRestoreRequest() {
        val pipePath = Paths.get(System.getProperty("java.io.tmpdir"), PIPE_NAME)
        if (Files.exists(pipePath)) {
            BufferedWriter(OutputStreamWriter(Files.newOutputStream(pipePath))).use { writer ->
                writer.write("RESTORE\n")
                writer.flush()
            }
        }
    }

    private fun releaseLock() {
        try {
            fileLock?.release()
            fileChannel?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
