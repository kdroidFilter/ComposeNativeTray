package com.kdroid.composetray.demo

import com.kdroid.kmplog.Log
import com.kdroid.kmplog.d

object MemoryLogger {
    fun logMemoryUsage() {
        val runtime = Runtime.getRuntime()

        val maxMemory = runtime.maxMemory() / (1024 * 1024) // Convert to MB
        val allocatedMemory = runtime.totalMemory() / (1024 * 1024) // Convert to MB
        val freeMemory = runtime.freeMemory() / (1024 * 1024) // Convert to MB
        val usedMemory = allocatedMemory - freeMemory

        Log.d("MemoryLogger", "Max memory: $maxMemory MB")
        Log.d("MemoryLogger", "Allocated memory: $allocatedMemory MB")
        Log.d("MemoryLogger", "Used memory: $usedMemory MB")
        Log.d("MemoryLogger", "Free memory: $freeMemory MB")
    }
}