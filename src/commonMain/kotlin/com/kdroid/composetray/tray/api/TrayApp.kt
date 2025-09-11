package com.kdroid.composetray.tray.api

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberDialogState
import com.kdroid.composetray.lib.linux.LinuxOutsideClickWatcher
import com.kdroid.composetray.lib.mac.MacOSWindowManager
import com.kdroid.composetray.lib.mac.MacOutsideClickWatcher
import com.kdroid.composetray.menu.api.TrayMenuBuilder
import com.kdroid.composetray.utils.*
import io.github.kdroidfilter.platformtools.OperatingSystem
import io.github.kdroidfilter.platformtools.OperatingSystem.MACOS
import io.github.kdroidfilter.platformtools.OperatingSystem.WINDOWS
import io.github.kdroidfilter.platformtools.getOperatingSystem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.awt.EventQueue.invokeLater
import java.awt.event.WindowEvent
import java.awt.event.WindowFocusListener


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
@ExperimentalTrayAppApi
@Composable
fun ApplicationScope.TrayApp(
    icon: ImageVector,
    tint: Color? = null,
    iconRenderProperties: IconRenderProperties = IconRenderProperties.forCurrentOperatingSystem(),
    tooltip: String,
    windowSize: DpSize = DpSize(300.dp, 200.dp),
    visibleOnStart: Boolean = false,
    fadeDurationMs: Int = 200,
    animationSpec: AnimationSpec<Float> =  tween(durationMillis = fadeDurationMs, easing = EaseInOut),
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
        fadeDurationMs = fadeDurationMs,
        animationSpec = animationSpec,
        content = content,
        menu = menu,
    )
}


/**
 * TrayApp overload: accepts a Painter as tray icon.
 */
@ExperimentalTrayAppApi
@Composable
fun ApplicationScope.TrayApp(
    icon: Painter,
    iconRenderProperties: IconRenderProperties = IconRenderProperties.forCurrentOperatingSystem(),
    tooltip: String,
    windowSize: DpSize = DpSize(300.dp, 200.dp),
    visibleOnStart: Boolean = false,
    fadeDurationMs: Int = 200,
    animationSpec: AnimationSpec<Float> =  tween(durationMillis = fadeDurationMs, easing = EaseInOut),
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
        fadeDurationMs = fadeDurationMs,
        animationSpec = animationSpec,
        content = content,
        menu = menu,
    )
}

/**
 * TrayApp overload: accepts platform-specific icon types similar to Tray:
 * - Windows: Painter
 * - macOS/Linux: ImageVector (with optional tint)
 */
@ExperimentalTrayAppApi
@Composable
fun ApplicationScope.TrayApp(
    windowsIcon: Painter,
    macLinuxIcon: ImageVector,
    tint: Color? = null,
    iconRenderProperties: IconRenderProperties = IconRenderProperties.forCurrentOperatingSystem(),
    tooltip: String,
    windowSize: DpSize = DpSize(300.dp, 200.dp),
    visibleOnStart: Boolean = false,
    fadeDurationMs: Int = 200,
    animationSpec: AnimationSpec<Float> =  tween(durationMillis = fadeDurationMs, easing = EaseInOut),
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
            fadeDurationMs = fadeDurationMs,
            animationSpec = animationSpec,
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
            fadeDurationMs = fadeDurationMs,
            animationSpec = animationSpec,
            menu = menu,
            content = content,
        )
    }
}

/**
 * TrayApp overload: accepts a DrawableResource directly.
 */
@ExperimentalTrayAppApi
@Composable
fun ApplicationScope.TrayApp(
    icon: DrawableResource,
    iconRenderProperties: IconRenderProperties = IconRenderProperties.forCurrentOperatingSystem(),
    tooltip: String,
    windowSize: DpSize = DpSize(300.dp, 200.dp),
    visibleOnStart: Boolean = false,
    fadeDurationMs: Int = 200,
    animationSpec: AnimationSpec<Float> =  tween(durationMillis = fadeDurationMs, easing = EaseInOut),
    menu: (TrayMenuBuilder.() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    // Convert DrawableResource to Painter and delegate to Painter overload
    val painter = painterResource(icon)
    TrayApp(
        icon = painter,
        iconRenderProperties = iconRenderProperties,
        tooltip = tooltip,
        windowSize = windowSize,
        visibleOnStart = visibleOnStart,
        fadeDurationMs = fadeDurationMs,
        animationSpec = animationSpec,
        menu = menu,
        content = content,
    )
}

@ExperimentalTrayAppApi
@Composable
fun ApplicationScope.TrayApp(
    windowsIcon: DrawableResource,
    macLinuxIcon: ImageVector,
    tint: Color? = null,
    iconRenderProperties: IconRenderProperties = IconRenderProperties.forCurrentOperatingSystem(),
    tooltip: String,
    windowSize: DpSize = DpSize(300.dp, 200.dp),
    visibleOnStart: Boolean = false,
    fadeDurationMs: Int = 200,
    animationSpec: AnimationSpec<Float> =  tween(durationMillis = fadeDurationMs, easing = EaseInOut),
    menu: (TrayMenuBuilder.() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val os = getOperatingSystem()
    if (os == WINDOWS) {
        val painter = painterResource(windowsIcon)
        TrayApp(
            icon = painter,
            iconRenderProperties = iconRenderProperties,
            tooltip = tooltip,
            windowSize = windowSize,
            visibleOnStart = visibleOnStart,
            fadeDurationMs = fadeDurationMs,
            animationSpec = animationSpec,
            menu = menu,
            content = content,
        )
    } else {
        TrayApp(
            icon = macLinuxIcon,
            tint = tint,
            iconRenderProperties = iconRenderProperties,
            tooltip = tooltip,
            windowSize = windowSize,
            visibleOnStart = visibleOnStart,
            fadeDurationMs = fadeDurationMs,
            animationSpec = animationSpec,
            menu = menu,
            content = content,
        )
    }
}

/**
 * TrayApp overload: accepts a composable iconContent with fade in/out animation.
 */
@ExperimentalTrayAppApi
@Composable
fun ApplicationScope.TrayApp(
    iconContent: @Composable () -> Unit,
    iconRenderProperties: IconRenderProperties = IconRenderProperties.forCurrentOperatingSystem(),
    tooltip: String,
    windowSize: DpSize = DpSize(300.dp, 200.dp),
    visibleOnStart: Boolean = false,
    fadeDurationMs: Int = 200,
    animationSpec: AnimationSpec<Float> =  tween(durationMillis = fadeDurationMs, easing = EaseInOut),
    menu: (TrayMenuBuilder.() -> Unit)? = null,
    content: @Composable () -> Unit,
    ) {
    var isVisible by remember { mutableStateOf(false) }
    var shouldShowWindow by remember { mutableStateOf(false) }

    val isDark = isMenuBarInDarkMode()
    val os = getOperatingSystem()

    // Simple global debounce to stabilize show/hide toggle across platforms
    var lastPrimaryActionAt by remember { mutableStateOf(0L) }
    val toggleDebounceMs = 280L

    // Stabilization windows: enforce minimum visible/hidden durations
    var lastShownAt by remember { mutableStateOf(0L) }
    var lastHiddenAt by remember { mutableStateOf(0L) }
    val minVisibleDurationMs = 350L
    val minHiddenDurationMs = 250L

    // Animation de l'opacité
    val alpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = animationSpec,
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
    // On Windows, delay auto-hide on startup when visibleOnStart is true to avoid immediate disappearance
    var autoHideEnabledAt by remember { mutableStateOf(0L) }

    // Helper to request hide with a minimum visible duration guard
    val requestHide: () -> Unit = {
        val now = System.currentTimeMillis()
        val sinceShow = now - lastShownAt
        if (sinceShow >= minVisibleDurationMs) {
            isVisible = false
            lastHiddenAt = System.currentTimeMillis()
        } else {
            val wait = minVisibleDurationMs - sinceShow
            CoroutineScope(Dispatchers.IO).launch {
                delay(wait)
                isVisible = false
                lastHiddenAt = System.currentTimeMillis()
            }
        }
    }

    // Primary action: toggle visibility with global debounce and Windows-specific guard
    val internalPrimaryAction: () -> Unit = {
        val now = System.currentTimeMillis()
        // Global debounce across all OS to avoid double execution due to rapid duplicate events
        if (now - lastPrimaryActionAt >= toggleDebounceMs) {
            lastPrimaryActionAt = now

            if (isVisible) {
                // Hide with guard
                val sinceShow = now - lastShownAt
                if (sinceShow >= minVisibleDurationMs) {
                    isVisible = false
                    lastHiddenAt = System.currentTimeMillis()
                } else {
                    val wait = minVisibleDurationMs - sinceShow
                    CoroutineScope(Dispatchers.IO).launch {
                        delay(wait)
                        isVisible = false
                        lastHiddenAt = System.currentTimeMillis()
                    }
                }
            } else {
                // Enforce a short minimum hidden duration to avoid hide->show bounce
                if (now - lastHiddenAt >= minHiddenDurationMs) {
                    // On Windows, ignore a re-show that happens immediately after focus loss
                    if (os == WINDOWS && (now - lastFocusLostAt) < 300) {
                        // Ignore
                    } else {
                        isVisible = true
                        lastShownAt = System.currentTimeMillis()
                    }
                }
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

        if (os == MACOS) {
            // Give the status item some time to settle
            delay(100)
        }
        if (os == WINDOWS) {
            // Wait briefly for this instance's initial tray position to be captured on the tray thread
            val deadline = System.currentTimeMillis() + 2000
            val key = tray.instanceKey()
            while (TrayClickTracker.getLastClickPosition(key) == null &&
                System.currentTimeMillis() < deadline) {
                delay(50)
            }
            // Provide a short grace period where focus-loss won't auto-hide the window
            autoHideEnabledAt = System.currentTimeMillis() + 1000
        }
        isVisible = true
        lastShownAt = System.currentTimeMillis()
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
        val windowPosition = getTrayWindowPositionForInstance(tray.instanceKey(), widthPx, heightPx)

        DialogWindow(
            onCloseRequest = { requestHide() },
            title = "",
            undecorated = true,
            resizable = false,
            focusable = true,
            alwaysOnTop = true,
            transparent = true,
            state = rememberDialogState(position = windowPosition, size = windowSize)
        ) {
            DisposableEffect(Unit) {
                // Mark this as the tray popup window so visibility checks can ignore it on macOS
                try { window.name = WindowVisibilityMonitor.TRAY_DIALOG_NAME } catch (_: Throwable) {}
                // Recompute visibility to avoid counting this tray popup as an app window
                runCatching { WindowVisibilityMonitor.recompute() }
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
                        if (os == WINDOWS && lastFocusLostAt < autoHideEnabledAt) {
                            // Ignore focus loss during startup grace period on Windows
                            return
                        }
                        requestHide()
                    }
                }

                // macOS outside-click watcher
                val macWatcher = if (getOperatingSystem() == MACOS) {
                    MacOutsideClickWatcher(
                        windowSupplier = { window },
                        onOutsideClick = { invokeLater { requestHide() } }
                    ).also { it.start() }
                } else null

                // Linux outside-click watcher (X11/XWayland). Safe no-op if X11 is not available.
                val linuxWatcher = if (getOperatingSystem() == OperatingSystem.LINUX) {
                    LinuxOutsideClickWatcher(
                        windowSupplier = { window },
                        onOutsideClick = { invokeLater { requestHide() } }
                    ).also { it.start() }
                } else null

                window.addWindowFocusListener(focusListener)

                onDispose {
                    window.removeWindowFocusListener(focusListener)
                    macWatcher?.stop()
                    linuxWatcher?.stop()
                    // Recompute visibility when closing the tray popup
                    runCatching { WindowVisibilityMonitor.recompute() }
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