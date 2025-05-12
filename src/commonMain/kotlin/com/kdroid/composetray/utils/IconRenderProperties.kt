package com.kdroid.composetray.utils

import androidx.compose.ui.unit.Density
import io.github.kdroidfilter.platformtools.OperatingSystem
import io.github.kdroidfilter.platformtools.getOperatingSystem


/**
 * Properties for rendering a Composable icon.
 *
 * @property sceneWidth Width of the [androidx.compose.ui.ImageComposeScene] in pixels
 * @property sceneHeight Height of the [androidx.compose.ui.ImageComposeScene] in pixels
 * @property sceneDensity Density for [androidx.compose.ui.ImageComposeScene]
 * @property targetWidth Width of the rendered icon in pixels
 * @property targetHeight Height of the rendered icon in pixels
 */
data class IconRenderProperties(
    val sceneWidth: Int = 192,
    val sceneHeight: Int = 192,
    val sceneDensity: Density = Density(2f),
    val targetWidth: Int = 192,
    val targetHeight: Int = 192
) {
    val requiresScaling = sceneWidth != targetWidth || sceneHeight != targetHeight

    companion object {

        /**
         * Provides an [IconRenderProperties] configured for the current operating system.
         *
         * This method determines the rendering size based on the current operating system,
         * defaulting to specific dimensions for Windows, macOS, and Linux. For unsupported operating
         * systems, it defaults to the provided scene width and height.
         *
         * @param sceneWidth Width of the [androidx.compose.ui.ImageComposeScene] in pixels.
         * @param sceneHeight Height of the [androidx.compose.ui.ImageComposeScene] in pixels.
         * @param density Density of the [androidx.compose.ui.ImageComposeScene].
         * @return An instance of [IconRenderProperties] with the appropriate target width and height
         *         based on the operating system.
         */
        fun forCurrentOperatingSystem(
            sceneWidth: Int = 192,
            sceneHeight: Int = 192,
            density: Density = Density(2f)
        ): IconRenderProperties {
            val (targetWidth, targetHeight) = when (getOperatingSystem()) {
                OperatingSystem.WINDOWS -> 32 to 32
                OperatingSystem.MACOS -> 44 to 44
                OperatingSystem.LINUX -> 24 to 24
                else -> sceneWidth to sceneHeight
            }

            return IconRenderProperties(
                sceneWidth = sceneWidth,
                sceneHeight = sceneHeight,
                sceneDensity = density,
                targetWidth = targetWidth,
                targetHeight = targetHeight
            )
        }

        /**
         * Provides an [IconRenderProperties] configured with settings that don't force icon scaling and aliasing.
         *
         * @param sceneWidth Width of the [androidx.compose.ui.ImageComposeScene] in pixels.
         * @param sceneHeight Height of the [androidx.compose.ui.ImageComposeScene] in pixels.
         * @param density Density of the [androidx.compose.ui.ImageComposeScene].
         * @return An instance of [IconRenderProperties] with the appropriate target width and height based on the operating system.
         */
        fun withoutScalingAndAliasing(
            sceneWidth: Int = 192,
            sceneHeight: Int = 192,
            density: Density = Density(2f)
        ) = IconRenderProperties(
            sceneWidth = sceneWidth,
            sceneHeight = sceneHeight,
            sceneDensity = density,
            targetWidth = sceneWidth,
            targetHeight = sceneHeight
        )
    }
}