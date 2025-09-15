package com.kdroid.composetray.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import kotlinx.coroutines.Dispatchers
import org.jetbrains.skia.*
import java.io.File
import java.util.zip.CRC32

/**
 * Utility functions for rendering Composable icons to image files for use in system tray.
 */
object ComposableIconUtils {

    private var currentRenderApi: RenderApi = RenderApi.AUTO

    enum class RenderApi {
        AUTO,
        DIRECTX12,
        OPENGL,
        SOFTWARE
    }

    init {
        // Start with automatic detection, will fallback if needed
        configureRenderingApi(RenderApi.AUTO)
    }

    /**
     * Configure Skiko rendering API with fallback mechanism
     */
    private fun configureRenderingApi(api: RenderApi) {
        try {
            when (api) {
                RenderApi.AUTO -> {
                    // Let Skiko choose automatically (usually tries DirectX12 first on Windows)
                    System.clearProperty("skiko.renderApi")
                    debugln { "[ComposableIconUtils] Using automatic rendering API selection" }
                }
                RenderApi.DIRECTX12 -> {
                    System.setProperty("skiko.renderApi", "DIRECT3D")
                    debugln { "[ComposableIconUtils] Configured DirectX12 rendering" }
                }
                RenderApi.OPENGL -> {
                    System.setProperty("skiko.renderApi", "OPENGL")
                    System.setProperty("skiko.rendering.api", "OPENGL")
                    debugln { "[ComposableIconUtils] Configured OpenGL rendering" }
                }
                RenderApi.SOFTWARE -> {
                    System.setProperty("skiko.renderApi", "SOFTWARE")
                    System.setProperty("skiko.softwareRendering", "true")
                    System.setProperty("skiko.rendering.api", "SOFTWARE")
                    System.setProperty("skiko.rendering.useGpu", "false")
                    System.setProperty("skiko.directx.disabled", "true")
                    debugln { "[ComposableIconUtils] Configured software rendering" }
                }
            }
            currentRenderApi = api
        } catch (e: Exception) {
            errorln { "[ComposableIconUtils] Failed to configure rendering API: ${e.message}" }
        }
    }

    /**
     * Try the next rendering API in the fallback chain
     */
    private fun tryNextRenderApi(): Boolean {
        return when (currentRenderApi) {
            RenderApi.AUTO, RenderApi.DIRECTX12 -> {
                debugln { "[ComposableIconUtils] Falling back from $currentRenderApi to OpenGL" }
                configureRenderingApi(RenderApi.OPENGL)
                true
            }
            RenderApi.OPENGL -> {
                debugln { "[ComposableIconUtils] Falling back from OpenGL to Software rendering" }
                configureRenderingApi(RenderApi.SOFTWARE)
                true
            }
            RenderApi.SOFTWARE -> {
                errorln { "[ComposableIconUtils] Already using software rendering, no more fallbacks available" }
                false
            }
        }
    }

    /**
     * Renders a Composable to a PNG file and returns the path to the file.
     *
     * @param iconRenderProperties Properties for rendering the icon
     * @param content The Composable content to render
     * @return Path to the generated PNG file
     * @throws Exception if rendering fails after all fallback attempts
     */
    fun renderComposableToPngFile(
        iconRenderProperties: IconRenderProperties,
        content: @Composable () -> Unit
    ): String {
        val tempFile = createTempFile(suffix = ".png")
        val pngData = renderComposableToPngBytesWithFallback(iconRenderProperties, content)
        tempFile.writeBytes(pngData)
        return tempFile.absolutePath
    }

    /**
     * Renders a Composable to PNG bytes with automatic fallback between rendering APIs
     */
    private fun renderComposableToPngBytesWithFallback(
        iconRenderProperties: IconRenderProperties,
        content: @Composable () -> Unit
    ): ByteArray {
        var attempts = 0
        val maxAttempts = 3 // Try DirectX/Auto, then OpenGL, then Software
        var lastException: Exception? = null

        while (attempts < maxAttempts) {
            try {
                debugln { "[ComposableIconUtils] Render attempt ${attempts + 1} with $currentRenderApi" }
                return renderComposableToPngBytes(iconRenderProperties, content)
            } catch (e: Exception) {
                lastException = e
                val errorMessage = e.message ?: ""
                val errorClassName = e.javaClass.simpleName

                errorln { "[ComposableIconUtils] $errorClassName with ${currentRenderApi}: $errorMessage" }

                // Check if the error is related to DirectX, OpenGL, or rendering in general
                val isDirectXError = errorMessage.contains("DirectX12", ignoreCase = true) ||
                        errorMessage.contains("D3D12", ignoreCase = true) ||
                        errorMessage.contains("Direct3D", ignoreCase = true) ||
                        errorMessage.contains("choose DirectX12 adapter", ignoreCase = true) ||
                        errorClassName.contains("RenderException", ignoreCase = true)

                val isOpenGLError = errorMessage.contains("OpenGL", ignoreCase = true) ||
                        errorMessage.contains("GL", ignoreCase = true)

                val isRenderingError = errorClassName.contains("Render", ignoreCase = true) ||
                        errorMessage.contains("render", ignoreCase = true)

                // If it's a rendering-related error, try the next API
                if (isDirectXError || isOpenGLError || isRenderingError) {
                    if (tryNextRenderApi()) {
                        attempts++
                        continue // Try again with the next API
                    }
                }

                // If we can't identify the error or no more fallbacks, throw the exception
                break
            }
        }

        // If all attempts failed, throw the last exception
        throw lastException ?: Exception("Failed to render composable after $attempts attempts")
    }

    /**
     * Renders a Composable to a PNG image and returns the result as a byte array.
     *
     * @param iconRenderProperties Properties for rendering the icon
     * @param content The Composable content to render
     * @return A byte array containing the rendered PNG image data.
     * @throws Exception if rendering fails
     */
    @Throws(Exception::class)
    fun renderComposableToPngBytes(
        iconRenderProperties: IconRenderProperties,
        content: @Composable () -> Unit
    ): ByteArray {
        var scene: ImageComposeScene? = null
        var renderedIcon: Image? = null
        var scaledBitmap: Bitmap? = null
        var scaledImage: Image? = null

        try {
            // Create the scene - this is where DirectX/OpenGL errors typically occur
            scene = ImageComposeScene(
                width = iconRenderProperties.sceneWidth,
                height = iconRenderProperties.sceneHeight,
                density = iconRenderProperties.sceneDensity,
                coroutineContext = Dispatchers.Unconfined
            ) {
                content()
            }

            // Render the scene - this may also trigger rendering API errors
            renderedIcon = scene.render()

            val iconData = if (iconRenderProperties.requiresScaling) {
                scaledBitmap = Bitmap().apply {
                    allocN32Pixels(iconRenderProperties.targetWidth, iconRenderProperties.targetHeight)
                }

                renderedIcon.scalePixels(
                    scaledBitmap.peekPixels()!!,
                    FilterMipmap(FilterMode.LINEAR, MipmapMode.LINEAR),
                    true
                )

                scaledImage = Image.makeFromBitmap(scaledBitmap)
                scaledImage.encodeToData(EncodedImageFormat.PNG)
                    ?: throw Exception("Failed to encode scaled image to PNG")
            } else {
                renderedIcon.encodeToData(EncodedImageFormat.PNG)
                    ?: throw Exception("Failed to encode image to PNG")
            }

            return iconData.bytes
        } catch (e: Exception) {
            // Re-throw to be handled by the fallback wrapper
            throw e
        } finally {
            // Ensure proper cleanup
            try {
                scaledImage?.close()
                scaledBitmap?.close()
                renderedIcon?.close()
                scene?.close()
            } catch (e: Exception) {
                debugln { "[ComposableIconUtils] Error during cleanup: ${e.message}" }
            }
        }
    }

    /**
     * Renders a Composable to an ICO file and returns the path to the file.
     *
     * @param iconRenderProperties Properties for rendering the icon
     * @param content The Composable content to render
     * @return Path to the generated ICO file
     * @throws Exception if rendering fails after all fallback attempts
     */
    fun renderComposableToIcoFile(
        iconRenderProperties: IconRenderProperties,
        content: @Composable (() -> Unit)
    ): String {
        val tempFile = createTempFile(suffix = ".ico")
        val icoData = renderComposableToIcoBytes(iconRenderProperties, content)
        tempFile.writeBytes(icoData)
        return tempFile.absolutePath
    }

    /**
     * Renders a Composable to ICO format bytes.
     * Since ICO format is not directly supported by the encoding library,
     * this method first renders to PNG and then creates a simple ICO wrapper.
     *
     * @param iconRenderProperties Properties for rendering the icon
     * @param content The Composable content to render
     * @return Byte array containing the ICO data
     * @throws Exception if rendering fails after all fallback attempts
     */
    fun renderComposableToIcoBytes(
        iconRenderProperties: IconRenderProperties,
        content: @Composable () -> Unit
    ): ByteArray {
        // First render to PNG format (with fallback support)
        val pngBytes = renderComposableToPngBytesWithFallback(iconRenderProperties, content)

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
        icoData[6] = iconRenderProperties.targetWidth.toByte() // Width (0 means 256)
        icoData[7] = iconRenderProperties.targetHeight.toByte() // Height (0 means 256)
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
     * Resets the rendering API to try again from the beginning of the fallback chain.
     * Useful when creating new instances or after configuration changes.
     */
    fun resetRenderingApi() {
        debugln { "[ComposableIconUtils] Resetting rendering API to AUTO" }
        configureRenderingApi(RenderApi.AUTO)
    }

    /**
     * Gets the current rendering API being used
     */
    fun getCurrentRenderApi(): String = currentRenderApi.name

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
     * @param iconRenderProperties Properties for rendering the icon
     * @param content The Composable content to render
     * @return A hash value representing the current state of the composable content
     */
    @Composable
    fun calculateContentHash(
        iconRenderProperties: IconRenderProperties,
        content: @Composable () -> Unit
    ): Long {
        return try {
            // Render the composable to PNG bytes (with fallback support)
            val pngBytes = renderComposableToPngBytesWithFallback(iconRenderProperties, content)

            // Calculate CRC32 hash of the PNG bytes
            val crc = CRC32()
            crc.update(pngBytes)
            crc.value
        } catch (e: Exception) {
            errorln { "[ComposableIconUtils] Failed to calculate content hash: ${e.message}" }
            // Return a time-based hash as fallback
            System.currentTimeMillis()
        }
    }
}