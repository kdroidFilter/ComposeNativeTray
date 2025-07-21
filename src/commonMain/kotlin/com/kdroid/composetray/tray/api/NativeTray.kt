package com.kdroid.composetray.tray.api

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.window.ApplicationScope
import com.kdroid.composetray.menu.api.TrayMenuBuilder
import com.kdroid.composetray.tray.impl.AwtTrayInitializer
import com.kdroid.composetray.tray.impl.LinuxTrayInitializer
import com.kdroid.composetray.tray.impl.MacTrayInitializer
import com.kdroid.composetray.tray.impl.WindowsTrayInitializer
import com.kdroid.composetray.utils.ComposableIconUtils
import com.kdroid.composetray.utils.IconRenderProperties
import com.kdroid.composetray.utils.MenuContentHash
import com.kdroid.composetray.utils.extractToTempIfDifferent
import com.kdroid.kmplog.Log
import com.kdroid.kmplog.d
import com.kdroid.kmplog.e
import io.github.kdroidfilter.platformtools.OperatingSystem.LINUX
import io.github.kdroidfilter.platformtools.OperatingSystem.MACOS
import io.github.kdroidfilter.platformtools.OperatingSystem.UNKNOWN
import io.github.kdroidfilter.platformtools.OperatingSystem.WINDOWS
import io.github.kdroidfilter.platformtools.getOperatingSystem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean


internal class NativeTray {

    private val trayScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val awtTrayUsed = AtomicBoolean(false)

    private val os = getOperatingSystem()
    private var initialized = false

    fun update(
        iconPath: String,
        windowsIconPath: String = iconPath,
        tooltip: String,
        primaryAction: (() -> Unit)?,
        primaryActionLabel: String,
        menuContent: (TrayMenuBuilder.() -> Unit)?
    ) {
        if (!initialized) {
            initializeTray(iconPath, windowsIconPath, tooltip, primaryAction, primaryActionLabel, menuContent)
            initialized = true
            return
        }

        try {
            when (os) {
                LINUX -> LinuxTrayInitializer.update(iconPath, tooltip, primaryAction, primaryActionLabel, menuContent)
                WINDOWS -> WindowsTrayInitializer.update(windowsIconPath, tooltip, primaryAction, menuContent)
                MACOS -> MacTrayInitializer.update(iconPath, tooltip, primaryAction, menuContent)
                UNKNOWN -> {
                    AwtTrayInitializer.update(iconPath, tooltip, primaryAction, primaryActionLabel, menuContent)
                    awtTrayUsed.set(true)
                }
                else -> {}
            }
        } catch (th: Throwable) {
            Log.e("NativeTray", "Error updating tray:", th)
        }
    }

    fun dispose() {
        when (os) {
            LINUX -> LinuxTrayInitializer.dispose()
            WINDOWS -> WindowsTrayInitializer.dispose()
            MACOS -> MacTrayInitializer.dispose()
            UNKNOWN -> if (awtTrayUsed.get()) AwtTrayInitializer.dispose()
            else -> {}
        }
        initialized = false
    }

    /**
     * Constructor that accepts file paths for icons
     * @deprecated Use the constructor with composable icon content instead
     */
    @Deprecated(
        message = "Use the constructor with composable icon content instead",
        replaceWith = ReplaceWith("NativeTray(iconContent, tooltip, primaryAction, primaryActionLabel, menuContent)")
    )
    private fun initializeTray(
        iconPath: String,
        windowsIconPath: String = iconPath,
        tooltip: String = "",
        primaryAction: (() -> Unit)?,
        primaryActionLabel: String,
        menuContent: (TrayMenuBuilder.() -> Unit)? = null
    ) {
        trayScope.launch {
            var trayInitialized = false
            val os = getOperatingSystem()
            try {
                when (os) {
                    LINUX -> {
                        Log.d("NativeTray", "Initializing Linux tray with icon path: $iconPath")
                        LinuxTrayInitializer.initialize(iconPath, tooltip, primaryAction, primaryActionLabel, menuContent)
                        trayInitialized = true
                    }

                    WINDOWS -> {
                        Log.d("NativeTray", "Initializing Windows tray with icon path: $windowsIconPath")
                        WindowsTrayInitializer.initialize(windowsIconPath, tooltip, primaryAction, menuContent)
                        trayInitialized = true
                    }

                    MACOS -> {
                        Log.d("NativeTray", "Initializing macOS tray with icon path: $iconPath")
                        MacTrayInitializer.initialize(iconPath, tooltip, primaryAction, menuContent)
                        trayInitialized = true
                    }

                    else -> {}
                }
            } catch (th: Throwable) {
                Log.e("NativeTray", "Error initializing tray:", th)
            }

            val awtTrayRequired = os == UNKNOWN || !trayInitialized
            if (awtTrayRequired) {
                if (AwtTrayInitializer.isSupported()) {
                    try {
                        Log.d("NativeTray", "Initializing AWT tray with icon path: $iconPath")
                        AwtTrayInitializer.initialize(iconPath, tooltip, primaryAction, primaryActionLabel, menuContent)
                        awtTrayUsed.set(true)
                    } catch (th: Throwable) {
                        Log.e("NativeTray", "Error initializing AWT tray:", th)
                    }
                } else {
                    Log.d("NativeTray", "AWT tray is not supported")
                }
            }
        }
    }

    /**
     * Constructor that accepts a Composable for the icon
     */
    private fun initializeTray(
        iconContent: @Composable () -> Unit,
        iconRenderProperties: IconRenderProperties = IconRenderProperties(),
        tooltip: String = "",
        primaryAction: (() -> Unit)?,
        primaryActionLabel: String,
        menuContent: (TrayMenuBuilder.() -> Unit)? = null
    ) {
        // Render the composable to PNG file for general use
        val pngIconPath = ComposableIconUtils.renderComposableToPngFile(iconRenderProperties, iconContent)
        Log.d("NativeTray", "Generated PNG icon path: $pngIconPath")

        // For Windows, we need an ICO file
        val windowsIconPath = if (getOperatingSystem() == WINDOWS) {
            // Create a temporary ICO file
            ComposableIconUtils.renderComposableToIcoFile(iconRenderProperties, iconContent).also {
                Log.d("NativeTray", "Generated Windows ICO path: $it")
            }
        } else {
            pngIconPath
        }

        initializeTray(pngIconPath, windowsIconPath, tooltip, primaryAction, primaryActionLabel, menuContent)
    }

}


/**
 * Configures and displays a system tray icon for the application with platform-specific behavior and menu options.
 *
 * @param iconPath The file path to the tray icon. This should be a valid image file compatible with the platform's tray requirements.
 * @param windowsIconPath The file path to the tray icon specifically for Windows. Defaults to the value of `iconPath`.
 * @param tooltip The tooltip text to be displayed when the user hovers over the tray icon.
 * @param primaryAction An optional callback to be invoked when the tray icon is clicked (handled only on specific platforms).
 * @param primaryActionLabel The label for the primary action on Linux and macOS. Defaults to "Open".
 * @param menuContent A lambda that builds the tray menu using a `TrayMenuBuilder`. Define the menu structure, including items, checkable items, dividers, and submenus.
 *
 * @deprecated Use the version with composable icon content instead
 */
@Deprecated(
    message = "Use the version with composable icon content instead",
    replaceWith = ReplaceWith("Tray(iconContent, tooltip, primaryAction, primaryActionLabel, menuContent)")
)
@Composable
fun ApplicationScope.Tray(
    iconPath: String,
    windowsIconPath: String = iconPath,
    tooltip: String,
    primaryAction: (() -> Unit)? = null,
    primaryActionLabel: String = "Open",
    menuContent: (TrayMenuBuilder.() -> Unit)? = null,
) {
    val absoluteIconPath = remember(iconPath) { extractToTempIfDifferent(iconPath)?.absolutePath.orEmpty() }
    val absoluteWindowsIconPath = remember(iconPath, windowsIconPath) {
        if (windowsIconPath == iconPath) absoluteIconPath
        else extractToTempIfDifferent(windowsIconPath)?.absolutePath.orEmpty()
    }

    val tray = remember { NativeTray() }

    // Calculate menu hash to detect changes
    val menuHash = MenuContentHash.calculateMenuHash(menuContent)

    // Update when params change, including menuHash
    LaunchedEffect(absoluteIconPath, absoluteWindowsIconPath, tooltip, primaryAction, primaryActionLabel, menuContent, menuHash) {
        tray.update(absoluteIconPath, absoluteWindowsIconPath, tooltip, primaryAction, primaryActionLabel, menuContent)
    }

    // Dispose only when Tray is removed from composition
    DisposableEffect(Unit) {
        onDispose {
            Log.d("NativeTray", "onDispose")
            tray.dispose()
        }
    }
}

/**
 * Configures and displays a system tray icon for the application with platform-specific behavior and menu options.
 * This version accepts a Composable for the icon instead of file paths.
 *
 * @param iconContent A Composable function that defines the icon to be displayed in the tray.
 * @param iconRenderProperties Properties for rendering the icon.
 * @param tooltip The tooltip text to be displayed when the user hovers over the tray icon.
 * @param primaryAction An optional callback to be invoked when the tray icon is clicked (handled only on specific platforms).
 * @param primaryActionLabel The label for the primary action on Linux and macOS. Defaults to "Open".
 * @param menuContent A lambda that builds the tray menu using a `TrayMenuBuilder`. Define the menu structure, including items, checkable items, dividers, and submenus.
 */
@Composable
fun ApplicationScope.Tray(
    iconContent: @Composable () -> Unit,
    iconRenderProperties: IconRenderProperties = IconRenderProperties.forCurrentOperatingSystem(),
    tooltip: String,
    primaryAction: (() -> Unit)? = null,
    primaryActionLabel: String = "Open",
    menuContent: (TrayMenuBuilder.() -> Unit)? = null,
) {
    val os = getOperatingSystem()
    // Calculate a hash of the rendered composable content to detect changes
    val contentHash = ComposableIconUtils.calculateContentHash(iconRenderProperties, iconContent)

    // Calculate a hash of the menu content to detect changes
    val menuHash = MenuContentHash.calculateMenuHash(menuContent)

    val pngIconPath = remember(contentHash) { ComposableIconUtils.renderComposableToPngFile(iconRenderProperties, iconContent) }
    val windowsIconPath = remember(contentHash) {
        if (os == WINDOWS) ComposableIconUtils.renderComposableToIcoFile(iconRenderProperties, iconContent) else pngIconPath
    }

    val tray = remember { NativeTray() }

    // Update when params change, including menuHash
    LaunchedEffect(pngIconPath, windowsIconPath, tooltip, primaryAction, primaryActionLabel, menuContent, contentHash, menuHash) {
        tray.update(pngIconPath, windowsIconPath, tooltip, primaryAction, primaryActionLabel, menuContent)
    }

    // Dispose only when Tray is removed from composition
    DisposableEffect(Unit) {
        onDispose {
            Log.d("NativeTray", "onDispose")
            tray.dispose()
        }
    }
}