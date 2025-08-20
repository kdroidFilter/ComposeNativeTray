package com.kdroid.composetray.tray.api

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberDialogState
import com.kdroid.composetray.menu.api.TrayMenuBuilder
import com.kdroid.composetray.utils.ComposableIconUtils
import com.kdroid.composetray.utils.IconRenderProperties
import com.kdroid.composetray.utils.MenuContentHash
import com.kdroid.composetray.utils.TrayClickTracker
import com.kdroid.composetray.utils.getNotificationAreaXYForWindows
import com.kdroid.composetray.utils.getStatusItemXYForMac
import com.kdroid.composetray.utils.getTrayWindowPosition
import com.kdroid.composetray.utils.isMenuBarInDarkMode
import io.github.kdroidfilter.platformtools.OperatingSystem.MACOS
import io.github.kdroidfilter.platformtools.OperatingSystem.WINDOWS
import io.github.kdroidfilter.platformtools.getOperatingSystem
import java.awt.EventQueue.invokeLater
import kotlinx.coroutines.delay


/**
 * TrayApp: High-level API that creates a system tray icon and an undecorated popup window
 * that toggles visibility on tray icon click and auto-hides when it loses focus.
 *
 * Parameters:
 * - icon: ImageVector for the tray icon (with optional tint that adapts like Tray overload)
 * - iconRenderProperties: Properties for rendering the icon to tray-compatible bitmaps
 * - tooltip: Tooltip text shown on tray icon hover
 * - windowSize: Size of the undecorated popup window
 * - transparent: Whether the popup window should be transparent (default: false)
 * - visibleOnStart: Whether the popup window is visible when the app starts (default: false)
 * - content: Composable content displayed inside the popup window
 * - menu: Optional context menu for the tray icon
 *
 * Differences from Tray:
 * - No primaryAction parameter: clicking the tray icon toggles the popup visibility
 * - Manages an undecorated always-on-top window that hides on focus loss
 */
@Composable
fun ApplicationScope.TrayApp(
    icon: ImageVector,
    tint: Color? = null,
    iconRenderProperties: IconRenderProperties = IconRenderProperties.forCurrentOperatingSystem(),
    tooltip: String,
    windowSize: DpSize = DpSize(300.dp, 200.dp),
    transparent: Boolean = false,
    visibleOnStart: Boolean = false,
    content: @Composable () -> Unit,
    menu: (TrayMenuBuilder.() -> Unit)? = null,
) {
    // Build the icon as composable content and delegate to the iconContent-based TrayApp
    val iconContent: @Composable () -> Unit = {
        val isDark = isMenuBarInDarkMode()
        Image(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            colorFilter = tint?.let { androidx.compose.ui.graphics.ColorFilter.tint(it) }
                ?: if (isDark) androidx.compose.ui.graphics.ColorFilter.tint(Color.White)
                else androidx.compose.ui.graphics.ColorFilter.tint(Color.Black)
        )
    }
    TrayApp(
        iconContent = iconContent,
        iconRenderProperties = iconRenderProperties,
        tooltip = tooltip,
        windowSize = windowSize,
        transparent = transparent,
        visibleOnStart = visibleOnStart,
        content = content,
        menu = menu,
    )
}


/**
 * TrayApp overload: accepts a Painter as tray icon.
 */
@Composable
fun ApplicationScope.TrayApp(
    icon: Painter,
    iconRenderProperties: IconRenderProperties = IconRenderProperties.forCurrentOperatingSystem(),
    tooltip: String,
    windowSize: DpSize = DpSize(300.dp, 200.dp),
    transparent: Boolean = false,
    visibleOnStart: Boolean = false,
    content: @Composable () -> Unit,
    menu: (TrayMenuBuilder.() -> Unit)? = null,
) {
    // Build the icon as composable content and delegate to the iconContent-based TrayApp
    val iconContent: @Composable () -> Unit = {
        Image(
            painter = icon,
            contentDescription = null,
            modifier = Modifier.fillMaxSize()
        )
    }
    TrayApp(
        iconContent = iconContent,
        iconRenderProperties = iconRenderProperties,
        tooltip = tooltip,
        windowSize = windowSize,
        transparent = transparent,
        visibleOnStart = visibleOnStart,
        content = content,
        menu = menu,
    )
}

/**
 * TrayApp overload: accepts a composable iconContent.
 */
@Composable
fun ApplicationScope.TrayApp(
    iconContent: @Composable () -> Unit,
    iconRenderProperties: IconRenderProperties = IconRenderProperties.forCurrentOperatingSystem(),
    tooltip: String,
    windowSize: DpSize = DpSize(300.dp, 200.dp),
    transparent: Boolean = false,
    visibleOnStart: Boolean = false,
    content: @Composable () -> Unit,
    menu: (TrayMenuBuilder.() -> Unit)? = null,
) {
    var isVisible by remember { mutableStateOf(false) }
    var suppressNextPrimaryAction by remember { mutableStateOf(false) }

    val isDark = isMenuBarInDarkMode() // force recomposition if menu bar theme changes and icon depends on it

    val os = getOperatingSystem()

    val contentHash = ComposableIconUtils.calculateContentHash(iconRenderProperties, iconContent) +
            isDark.hashCode()

    val menuHash = MenuContentHash.calculateMenuHash(menu)

    val pngIconPath = remember(contentHash) { ComposableIconUtils.renderComposableToPngFile(iconRenderProperties, iconContent) }
    val windowsIconPath = remember(contentHash) {
        if (os == WINDOWS) ComposableIconUtils.renderComposableToIcoFile(iconRenderProperties, iconContent) else pngIconPath
    }

    val tray = remember { NativeTray() }

    val internalPrimaryAction: () -> Unit = {
        if (os == WINDOWS && suppressNextPrimaryAction) {
            // Suppress the immediate next primary action on Windows after deactivation/focus loss
            suppressNextPrimaryAction = false
        } else {
            isVisible = !isVisible
        }
    }

    LaunchedEffect(pngIconPath, windowsIconPath, tooltip, internalPrimaryAction, menu, contentHash, menuHash) {
        tray.update(pngIconPath, windowsIconPath, tooltip, internalPrimaryAction, menu)
    }

    // If the window should be visible on start, wait for the native lib to provide
    // a reliable position before showing the window (especially important on Windows/macOS).
    LaunchedEffect(visibleOnStart, os) {
        if (!visibleOnStart) return@LaunchedEffect

        // Small grace delay to ensure native libs are loaded and ready
        var attempts = 0
        val maxAttempts = 20 // ~2 seconds at 100ms intervals
        when (os) {
            WINDOWS -> {
                while (attempts < maxAttempts && TrayClickTracker.getLastClickPosition() == null) {
                    runCatching { getNotificationAreaXYForWindows() }
                    if (TrayClickTracker.getLastClickPosition() != null) break
                    attempts++
                    delay(100)
                }
            }
            MACOS -> {
                while (attempts < maxAttempts && TrayClickTracker.getLastClickPosition() == null) {
                    val (x, y) = runCatching { getStatusItemXYForMac() }.getOrDefault(0 to 0)
                    if (x != 0 || y != 0) {
                        // We use current tray orientation from getTrayPosition()
                        val pos = com.kdroid.composetray.utils.getTrayPosition()
                        com.kdroid.composetray.utils.TrayClickTracker.setClickPosition(x, y, pos)
                        break
                    }
                    attempts++
                    delay(100)
                }
            }
            else -> {
                // For Linux or others, we don't have a synchronous query on start; show immediately
            }
        }
        isVisible = true
    }

    DisposableEffect(Unit) {
        onDispose { tray.dispose() }
    }

    DialogWindow(
        onCloseRequest = { },
        visible = false,
        state = rememberDialogState(
            size = DpSize(1.dp, 1.dp),
            position = WindowPosition(0.dp, 0.dp)
        ),
        transparent = true,
        undecorated = true,
        resizable = false,
        focusable = false,
    ) { }

    if (isVisible) {
        val widthPx = windowSize.width.value.toInt()
        val heightPx = windowSize.height.value.toInt()
        val windowPosition = getTrayWindowPosition(widthPx, heightPx)

        DialogWindow(
            onCloseRequest = { isVisible = false },
            title = "",
            undecorated = true,
            resizable = false,
            focusable = true,
            alwaysOnTop = true,
            transparent = transparent,
            state = rememberDialogState(position = windowPosition, size = windowSize)
        ) {
            DisposableEffect(Unit) {
                invokeLater {
                    try {
                        window.toFront()
                        window.requestFocus()
                        window.requestFocusInWindow()
                    } catch (_: Throwable) { }
                }
                val focusListener = object : java.awt.event.WindowFocusListener {
                    override fun windowGainedFocus(e: java.awt.event.WindowEvent?) {}
                    override fun windowLostFocus(e: java.awt.event.WindowEvent?) {
                        if (os == WINDOWS) suppressNextPrimaryAction = true
                        isVisible = false
                    }
                }
                val deactivationListener = object : java.awt.event.WindowAdapter() {
                    override fun windowDeactivated(e: java.awt.event.WindowEvent?) {
                        if (os == WINDOWS) suppressNextPrimaryAction = true
                        isVisible = false
                    }
                }
                window.addWindowFocusListener(focusListener)
                window.addWindowListener(deactivationListener)
                onDispose {
                    window.removeWindowFocusListener(focusListener)
                    window.removeWindowListener(deactivationListener)
                }
            }
            content()
        }
    }
}
