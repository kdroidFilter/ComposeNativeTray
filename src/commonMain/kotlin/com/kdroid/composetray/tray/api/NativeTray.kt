package com.kdroid.composetray.tray.api

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.window.ApplicationScope
import com.kdroid.composetray.menu.api.TrayMenuBuilder
import com.kdroid.composetray.tray.impl.AwtTrayInitializer
import com.kdroid.composetray.tray.impl.LinuxTrayInitializer
import com.kdroid.composetray.tray.impl.WindowsTrayInitializer
import com.kdroid.composetray.utils.ComposableIconUtils
import com.kdroid.composetray.utils.IconRenderProperties
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
    private val operatingSystem = getOperatingSystem()

    /**
     * Constructor that accepts file paths for icons
     * @deprecated Use the constructor with composable icon content instead
     */
    @Deprecated(
        message = "Use the constructor with composable icon content instead",
        replaceWith = ReplaceWith("NativeTray(iconContent, tooltip, primaryAction, primaryActionLinuxLabel, menuContent)")
    )
    constructor(
        iconPath: String,
        windowsIconPath: String = iconPath,
        tooltip: String = "",
        primaryAction: (() -> Unit)?,
        primaryActionLinuxLabel: String,
        menuContent: (TrayMenuBuilder.() -> Unit)? = null
    ) {
        initializeTray(iconPath, windowsIconPath, tooltip, primaryAction, primaryActionLinuxLabel, menuContent)
    }

    /**
     * Constructor that accepts a Composable for the icon
     */
    constructor(
        iconContent: @Composable () -> Unit,
        iconRenderProperties: IconRenderProperties = IconRenderProperties(),
        tooltip: String = "",
        primaryAction: (() -> Unit)?,
        primaryActionLinuxLabel: String,
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

        initializeTray(pngIconPath, windowsIconPath, tooltip, primaryAction, primaryActionLinuxLabel, menuContent)
    }

    private fun initializeTray(
        iconPath: String,
        windowsIconPath: String,
        tooltip: String,
        primaryAction: (() -> Unit)?,
        primaryActionLinuxLabel: String,
        menuContent: (TrayMenuBuilder.() -> Unit)? = null
    ) {
        trayScope.launch {
            var trayInitialized = false
            val os = getOperatingSystem()
            try {
                when (os) {
                    LINUX -> {
                        Log.d("NativeTray", "Initializing Linux tray with icon path: $iconPath")
                        LinuxTrayInitializer.initialize(iconPath, tooltip, primaryAction, primaryActionLinuxLabel, menuContent)
                        trayInitialized = true
                    }

                    WINDOWS -> {
                        Log.d("NativeTray", "Initializing Windows tray with icon path: $windowsIconPath")
                        WindowsTrayInitializer.initialize(windowsIconPath, tooltip, primaryAction, menuContent)
                        trayInitialized = true
                    }

                    else -> {}
                }
            } catch (e: Exception) {
                Log.e("NativeTray", "Error initializing tray:", e)
            }

            val awtTrayRequired = os == MACOS || os == UNKNOWN || !trayInitialized
            if (awtTrayRequired) {
                if (AwtTrayInitializer.isSupported()) {
                    try {
                        Log.d("NativeTray", "Initializing AWT tray with icon path: $iconPath")
                        AwtTrayInitializer.initialize(iconPath, tooltip, primaryAction, menuContent)
                        awtTrayUsed.set(true)
                    } catch (e: Exception) {
                        Log.e("NativeTray", "Error initializing AWT tray:", e)
                    }
                } else {
                    Log.d("NativeTray", "AWT tray is not supported")
                }
            }
        }
    }

    internal fun updateTooltip(text: String) {
        when {
            awtTrayUsed.get() -> AwtTrayInitializer.updateTooltip(text)
            operatingSystem == LINUX -> LinuxTrayInitializer.updateTooltip(text)
            operatingSystem == WINDOWS -> WindowsTrayInitializer.updateTooltip(text)
        }
    }

    internal fun updateMenu(
        menuContent: (TrayMenuBuilder.() -> Unit)?,
        primaryAction: (() -> Unit)?,
        primaryActionLinuxLabel: String
    ) {
        when {
            awtTrayUsed.get() -> AwtTrayInitializer.updateMenu(menuContent)
            operatingSystem == LINUX -> LinuxTrayInitializer.updateMenu(menuContent, primaryAction, primaryActionLinuxLabel)
            operatingSystem == WINDOWS -> WindowsTrayInitializer.updateMenu(menuContent)
        }
    }

    internal fun updateIconContent(iconContent: @Composable () -> Unit, renderProps: IconRenderProperties) {
        val pngIconPath = ComposableIconUtils.renderComposableToPngFile(renderProps, iconContent)
        val windowsIconPath = if (operatingSystem == WINDOWS) {
            ComposableIconUtils.renderComposableToIcoFile(renderProps, iconContent)
        } else {
            pngIconPath
        }
        when {
            awtTrayUsed.get() -> AwtTrayInitializer.updateIcon(pngIconPath)
            operatingSystem == LINUX -> LinuxTrayInitializer.updateIcon(pngIconPath)
            operatingSystem == WINDOWS -> WindowsTrayInitializer.updateIcon(windowsIconPath)
        }
    }

    internal fun dispose() {
        val os = getOperatingSystem()
        when {
            awtTrayUsed.get() -> AwtTrayInitializer.dispose()
            os == LINUX -> LinuxTrayInitializer.dispose()
            os == WINDOWS -> WindowsTrayInitializer.dispose()
            else -> {}
        }
    }
}


/**
 * Configures and displays a system tray icon for the application with platform-specific behavior and menu options.
 *
 * @param iconPath The file path to the tray icon. This should be a valid image file compatible with the platform's tray requirements.
 * @param windowsIconPath The file path to the tray icon specifically for Windows. Defaults to the value of `iconPath`.
 * @param tooltip The tooltip text to be displayed when the user hovers over the tray icon.
 * @param primaryAction An optional callback to be invoked when the tray icon is clicked (handled only on specific platforms).
 * @param primaryActionLinuxLabel The label for the primary action on Linux. Defaults to "Open".
 * @param menuContent A lambda that builds the tray menu using a `TrayMenuBuilder`. Define the menu structure, including items, checkable items, dividers, and submenus.
 * 
 * @deprecated Use the version with composable icon content instead
 */
@Deprecated(
    message = "Use the version with composable icon content instead",
    replaceWith = ReplaceWith("Tray(iconContent, tooltip, primaryAction, primaryActionLinuxLabel, menuContent)")
)
@Composable
fun ApplicationScope.Tray(
    iconPath: String,
    windowsIconPath: String = iconPath,
    tooltip: String,
    primaryAction: (() -> Unit)? = null,
    primaryActionLinuxLabel: String = "Open",
    menuContent: (TrayMenuBuilder.() -> Unit)? = null
) {
    val absoluteIconPath = remember(iconPath) { extractToTempIfDifferent(iconPath)?.absolutePath.orEmpty() }
    val absoluteWindowsIconPath = remember(iconPath, windowsIconPath) {
        if (windowsIconPath == iconPath) absoluteIconPath
        else extractToTempIfDifferent(windowsIconPath)?.absolutePath.orEmpty()
    }
    DisposableEffect(
        absoluteIconPath,
        absoluteWindowsIconPath,
        tooltip,
        primaryAction,
        primaryActionLinuxLabel,
        menuContent
    ) {
        val tray = NativeTray(
            iconPath = absoluteIconPath,
            windowsIconPath = absoluteWindowsIconPath,
            tooltip = tooltip,
            primaryAction = primaryAction,
            primaryActionLinuxLabel = primaryActionLinuxLabel,
            menuContent = menuContent
        )

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
 * @param primaryActionLinuxLabel The label for the primary action on Linux. Defaults to "Open".
 * @param menuContent A lambda that builds the tray menu using a `TrayMenuBuilder`. Define the menu structure, including items, checkable items, dividers, and submenus.
 */
@Composable
fun ApplicationScope.Tray(
    state: TrayState,
    iconContent: @Composable () -> Unit,
    iconRenderProperties: IconRenderProperties = IconRenderProperties.forCurrentOperatingSystem(),
    tooltip: String,
    primaryAction: (() -> Unit)? = null,
    primaryActionLinuxLabel: String = "Open",
    menuContent: (TrayMenuBuilder.() -> Unit)? = null,
) {
    LaunchedEffect(state) {
        state.initIfNeeded(iconContent, iconRenderProperties, tooltip, primaryAction, primaryActionLinuxLabel, menuContent)
    }

    LaunchedEffect(state, tooltip) { state.updateTooltip(tooltip) }
    LaunchedEffect(state, menuContent, primaryAction, primaryActionLinuxLabel) {
        state.updateMenuItems(menuContent, primaryAction, primaryActionLinuxLabel)
    }

    val currentHash = ComposableIconUtils.calculateContentHash(iconRenderProperties, iconContent)

    LaunchedEffect(state, currentHash) {
        if (currentHash != state.lastIconHash) {
            state.lastIconHash = currentHash
            state.updateIconContent(iconContent, iconRenderProperties)
        }
    }
}

@Composable
fun ApplicationScope.Tray(
    iconContent: @Composable () -> Unit,
    iconRenderProperties: IconRenderProperties = IconRenderProperties.forCurrentOperatingSystem(),
    tooltip: String,
    primaryAction: (() -> Unit)? = null,
    primaryActionLinuxLabel: String = "Open",
    menuContent: (TrayMenuBuilder.() -> Unit)? = null,
) {
    val state = rememberTrayState()
    Tray(state, iconContent, iconRenderProperties, tooltip, primaryAction, primaryActionLinuxLabel, menuContent)
}

@Composable
fun ApplicationScope.rememberTrayState(): TrayState {
    val state = remember { TrayState() }
    DisposableEffect(Unit) { onDispose { state.dispose() } }
    return state
}
