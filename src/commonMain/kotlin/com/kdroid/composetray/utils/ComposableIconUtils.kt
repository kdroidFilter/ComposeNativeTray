package com.kdroid.composetray.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.Dispatchers
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.io.File
import java.util.zip.CRC32

/**
 * Utility functions for rendering Composable icons to image files for use in system tray.
 */
object ComposableIconUtils {

    /**
     * Renders a Composable to a PNG file and returns the path to the file.
     *
     * @param width Width of the image in pixels
     * @param height Height of the image in pixels
     * @param density Density factor for rendering
     * @param content The Composable content to render
     * @return Path to the generated PNG file
     */
    fun renderComposableToPngFile(
        width: Int = 200,
        height: Int = 200,
        density: Float = 2f,
        content: @Composable () -> Unit
    ): String {
        val tempFile = createTempFile(suffix = ".png")
        val pngData = renderComposableToPngBytes(width, height, density, content)
        tempFile.writeBytes(pngData)
        return tempFile.absolutePath
    }


    fun renderComposableToPngBytes(
        width: Int = 200,
        height: Int = 200,
        density: Float = 2f,
        content: @Composable () -> Unit
    ): ByteArray {
        val scene = ImageComposeScene(
            width = width,
            height = height,
            density = Density(density),
            coroutineContext = Dispatchers.Unconfined
        ) {
            content()
        }

        val img = scene.render()
        val bitmap = Bitmap.makeFromImage(img)
        val data = Image.makeFromBitmap(bitmap).use { image ->
            image.encodeToData(EncodedImageFormat.PNG)!!
        }

        return data.bytes
    }


    /**
     * Renders a Composable to ICO format bytes.
     * Since ICO format is not directly supported by the encoding library,
     * this method first renders to PNG and then creates a simple ICO wrapper.
     *
     * @param width Width of the image in pixels
     * @param height Height of the image in pixels
     * @param density Density factor for rendering
     * @param content The Composable content to render
     * @return Byte array containing the ICO data
     */
    fun renderComposableToIcoBytes(
        width: Int = 200,
        height: Int = 200,
        density: Float = 2f,
        content: @Composable () -> Unit
    ): ByteArray {
        // First render to PNG format (which is supported)
        val pngBytes = renderComposableToPngBytes(width, height, density, content)

        // Create a simple ICO format wrapper around the PNG data
        // ICO header (6 bytes) + ICO directory entry (16 bytes) + PNG data
        val icoHeaderSize = 6
        val icoDirEntrySize = 16
        val icoData = ByteArray(icoHeaderSize + icoDirEntrySize + pngBytes.size)

        // ICO header
        icoData[0] = 0 // Reserved, must be 0
        icoData[1] = 0 // Reserved, must be 0
        icoData[2] = 1 // Type: 1 for ICO
        icoData[3] = 0 // Type: 1 for ICO (high byte)
        icoData[4] = 1 // Number of images
        icoData[5] = 0 // Number of images (high byte)

        // ICO directory entry
        icoData[6] = width.toByte() // Width (0 means 256)
        icoData[7] = height.toByte() // Height (0 means 256)
        icoData[8] = 0 // Color palette size (0 for no palette)
        icoData[9] = 0 // Reserved, must be 0
        icoData[10] = 1 // Color planes
        icoData[11] = 0 // Color planes (high byte)
        icoData[12] = 32 // Bits per pixel
        icoData[13] = 0 // Bits per pixel (high byte)

        // Size of image data in bytes
        val dataSize = pngBytes.size
        icoData[14] = (dataSize and 0xFF).toByte()
        icoData[15] = ((dataSize shr 8) and 0xFF).toByte()
        icoData[16] = ((dataSize shr 16) and 0xFF).toByte()
        icoData[17] = ((dataSize shr 24) and 0xFF).toByte()

        // Offset to image data
        val offset = icoHeaderSize + icoDirEntrySize
        icoData[18] = (offset and 0xFF).toByte()
        icoData[19] = ((offset shr 8) and 0xFF).toByte()
        icoData[20] = ((offset shr 16) and 0xFF).toByte()
        icoData[21] = ((offset shr 24) and 0xFF).toByte()

        // Copy PNG data
        System.arraycopy(pngBytes, 0, icoData, offset, pngBytes.size)

        return icoData
    }

    /**
     * Creates a temporary file that will be deleted when the JVM exits.
     */
    private fun createTempFile(prefix: String = "tray_icon_", suffix: String): File {
        val tempFile = File.createTempFile(prefix, suffix)
        tempFile.deleteOnExit()
        return tempFile
    }

    /**
     * Calculates a hash value for the rendered composable content.
     * This can be used to detect changes in the composable content without requiring an explicit key.
     *
     * @param width Width of the image in pixels
     * @param height Height of the image in pixels
     * @param density Density factor for rendering
     * @param content The Composable content to render
     * @return A hash value representing the current state of the composable content
     */
    @Composable
    fun calculateContentHash(
        width: Int = 200,
        height: Int = 200,
        density: Float = 2f,
        content: @Composable () -> Unit
    ): Long {
        // Render the composable to PNG bytes
        val pngBytes = renderComposableToPngBytes(width, height, density, content)

        // Calculate CRC32 hash of the PNG bytes
        val crc = CRC32()
        crc.update(pngBytes)
        return crc.value
    }
}
