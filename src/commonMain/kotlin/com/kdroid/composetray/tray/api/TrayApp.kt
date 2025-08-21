package com.kdroid.composetray.tray.api

import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberDialogState
import com.kdroid.composetray.lib.linux.LinuxOutsideClickWatcher
import com.kdroid.composetray.menu.api.TrayMenuBuilder
import com.kdroid.composetray.utils.ComposableIconUtils
import com.kdroid.composetray.utils.IconRenderProperties
import com.kdroid.composetray.utils.MenuContentHash
import com.kdroid.composetray.utils.TrayClickTracker
import com.kdroid.composetray.utils.getNotificationAreaXYForWindows
import com.kdroid.composetray.utils.getTrayWindowPosition
import com.kdroid.composetray.utils.isMenuBarInDarkMode
import io.github.kdroidfilter.platformtools.OperatingSystem.MACOS
import io.github.kdroidfilter.platformtools.OperatingSystem.WINDOWS
import io.github.kdroidfilter.platformtools.getOperatingSystem
import java.awt.EventQueue.invokeLater
import kotlinx.coroutines.delay
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.awt.event.WindowFocusListener
import com.kdroid.composetray.lib.mac.MacOutsideClickWatcher
import io.github.kdroidfilter.platformtools.OperatingSystem
import com.kdroid.composetray.utils.WindowVisibilityMonitor
import com.kdroid.composetray.lib.mac.MacOSWindowManager
import kotlinx.coroutines.flow.collectLatest


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
    visibleOnStart: Boolean = false,
    menu: (TrayMenuBuilder.() -> Unit)? = null,
    content: @Composable () -> Unit,
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
    visibleOnStart: Boolean = false,
    menu: (TrayMenuBuilder.() -> Unit)? = null,
    content: @Composable () -> Unit,
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
        visibleOnStart = visibleOnStart,
        content = content,
        menu = menu,
    )
}

/**
 * TrayApp overload: accepts platform-specific icon types similar to Tray:
 * - Windows: Painter
 * - macOS/Linux: ImageVector (with optional tint)
 */
@Composable
fun ApplicationScope.TrayApp(
    windowsIcon: Painter,
    macLinuxIcon: ImageVector,
    tint: Color? = null,
    iconRenderProperties: IconRenderProperties = IconRenderProperties.forCurrentOperatingSystem(),
    tooltip: String,
    windowSize: DpSize = DpSize(300.dp, 200.dp),
    visibleOnStart: Boolean = false,
    menu: (TrayMenuBuilder.() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val os = getOperatingSystem()
    if (os == WINDOWS) {
        // Delegate to Painter overload for Windows
        TrayApp(
            icon = windowsIcon,
            iconRenderProperties = iconRenderProperties,
            tooltip = tooltip,
            windowSize = windowSize,
            visibleOnStart = visibleOnStart,
            menu = menu,
            content = content,
        )
    } else {
        // Delegate to ImageVector overload for macOS/Linux
        TrayApp(
            icon = macLinuxIcon,
            tint = tint,
            iconRenderProperties = iconRenderProperties,
            tooltip = tooltip,
            windowSize = windowSize,
            visibleOnStart = visibleOnStart,
            menu = menu,
            content = content,
        )
    }
}

/**
 * TrayApp overload: accepts a composable iconContent with fade in/out animation.
 */
@Composable
fun ApplicationScope.TrayApp(
    iconContent: @Composable () -> Unit,
    iconRenderProperties: IconRenderProperties = IconRenderProperties.forCurrentOperatingSystem(),
    tooltip: String,
    windowSize: DpSize = DpSize(300.dp, 200.dp),
    visibleOnStart: Boolean = false,
    fadeDurationMs: Int = 200, // Durée de l'animation en ms
    menu: (TrayMenuBuilder.() -> Unit)? = null,
    content: @Composable () -> Unit,
    ) {
    var isVisible by remember { mutableStateOf(false) }
    var shouldShowWindow by remember { mutableStateOf(false) }

    val isDark = isMenuBarInDarkMode()
    val os = getOperatingSystem()

    // Animation de l'opacité
    val alpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(durationMillis = fadeDurationMs, easing = EaseInOut),
        label = "window_fade"
    )

    val contentHash = ComposableIconUtils.calculateContentHash(iconRenderProperties, iconContent) +
            isDark.hashCode()

    val menuHash = MenuContentHash.calculateMenuHash(menu)

    val pngIconPath = remember(contentHash) {
        ComposableIconUtils.renderComposableToPngFile(iconRenderProperties, iconContent)
    }
    val windowsIconPath = remember(contentHash) {
        if (os == WINDOWS)
            ComposableIconUtils.renderComposableToIcoFile(iconRenderProperties, iconContent)
        else pngIconPath
    }

    val tray = remember { NativeTray() }

    // Timestamp of the last focus loss to avoid Windows double-toggle (hide then immediate re-show)
    var lastFocusLostAt by remember { mutableStateOf(0L) }

    // Primary action: toggle visibility with Windows-specific debounce
    val internalPrimaryAction: () -> Unit = {
        val now = System.currentTimeMillis()
        if (isVisible) {
            // Hide
            isVisible = false
        } else {
            // On Windows, ignore a re-show that happens immediately after focus loss
            if (os == WINDOWS && (now - lastFocusLostAt) < 300) {
                // Ignore
            } else {
                isVisible = true
            }
        }
    }

    // Gestion de l'ouverture/fermeture de la fenêtre avec animation
    LaunchedEffect(isVisible) {
        if (isVisible) {
            // Ouvrir la fenêtre immédiatement pour commencer l'animation fade in
            shouldShowWindow = true
        } else {
            // Attendre la fin de l'animation fade out avant de fermer la fenêtre
            delay(fadeDurationMs.toLong())
            shouldShowWindow = false
        }
    }

    LaunchedEffect(pngIconPath, windowsIconPath, tooltip, internalPrimaryAction, menu, contentHash, menuHash) {
        tray.update(pngIconPath, windowsIconPath, tooltip, internalPrimaryAction, menu)
    }

    // On macOS, automatically manage Dock visibility based on whether any AWT window is visible
    LaunchedEffect(os) {
        if (os == MACOS) {
            WindowVisibilityMonitor.hasAnyVisibleWindows.collectLatest { hasVisible ->
                runCatching {
                    val manager = MacOSWindowManager()
                    if (hasVisible) manager.showInDock() else manager.hideFromDock()
                }
            }
        }
    }

    // Handle visibleOnStart
    LaunchedEffect(visibleOnStart, os) {
        if (!visibleOnStart) return@LaunchedEffect

        var attempts = 0
        val maxAttempts = 20
        when (os) {
            WINDOWS -> {
                // Try to fetch the notification area position so the window can appear near it
                while (attempts < maxAttempts && TrayClickTracker.getLastClickPosition() == null) {
                    runCatching { getNotificationAreaXYForWindows() }
                    if (TrayClickTracker.getLastClickPosition() != null) break
                    attempts++
                    delay(100)
                }
            }
            MACOS -> {
                // Give the status item some time to settle so getStatusItemXYForMac() is more reliable
                delay(500)
            }
            else -> {
                // Linux or others: nothing special here
            }
        }
        isVisible = true
    }

    DisposableEffect(Unit) {
        onDispose { tray.dispose() }
    }

    // Invisible helper window (required by Compose Desktop)
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

    // Main popup window
    if (shouldShowWindow) {
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
            transparent = true,
            state = rememberDialogState(position = windowPosition, size = windowSize)
        ) {
            DisposableEffect(Unit) {
                // Force bring-to-front on open
                invokeLater {
                    try {
                        window.toFront()
                        window.requestFocus()
                        window.requestFocusInWindow()
                    } catch (_: Throwable) {
                    }
                }

                val focusListener = object : WindowFocusListener {
                    override fun windowGainedFocus(e: WindowEvent?) = Unit
                    override fun windowLostFocus(e: WindowEvent?) {
                        lastFocusLostAt = System.currentTimeMillis()
                        isVisible = false
                    }
                }

                // macOS outside-click watcher
                val macWatcher = if (getOperatingSystem() == MACOS) {
                    MacOutsideClickWatcher(
                        windowSupplier = { window },
                        onOutsideClick = { invokeLater { isVisible = false } }
                    ).also { it.start() }
                } else null

                // Linux outside-click watcher (X11/XWayland). Safe no-op if X11 is not available.
                val linuxWatcher = if (getOperatingSystem() == OperatingSystem.LINUX) {
                    LinuxOutsideClickWatcher(
                        windowSupplier = { window },
                        onOutsideClick = { invokeLater { isVisible = false } }
                    ).also { it.start() }
                } else null

                window.addWindowFocusListener(focusListener)

                onDispose {
                    window.removeWindowFocusListener(focusListener)
                    macWatcher?.stop()
                    linuxWatcher?.stop()
                }
            }

            // Wrapper avec animation d'opacité
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(alpha)
            ) {
                content()
            }
        }
    }
}