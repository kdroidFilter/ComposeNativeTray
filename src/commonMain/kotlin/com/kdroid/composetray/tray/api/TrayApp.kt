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
import com.kdroid.composetray.lib.windows.WindowsOutsideClickWatcher
import com.kdroid.composetray.menu.api.TrayMenuBuilder
import com.kdroid.composetray.utils.*
import io.github.kdroidfilter.platformtools.OperatingSystem
import io.github.kdroidfilter.platformtools.OperatingSystem.MACOS
import io.github.kdroidfilter.platformtools.OperatingSystem.WINDOWS
import io.github.kdroidfilter.platformtools.getOperatingSystem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
 * IMPORTANT: This version keeps the window **mounted** at all times to preserve
 * composable state. We toggle actual visibility with the `visible` parameter and
 * fade the content with alpha. The composition is never destroyed, so internal
 * `remember`/`rememberSaveable` state of your `content()` is kept.
 */
@ExperimentalTrayAppApi
@Composable
fun ApplicationScope.TrayApp(
    icon: ImageVector,
    tint: Color? = null,
    iconRenderProperties: IconRenderProperties = IconRenderProperties.forCurrentOperatingSystem(),
    tooltip: String,
    state: TrayAppState? = null,
    windowSize: DpSize? = null, // Deprecated, use state.windowSize
    visibleOnStart: Boolean = false, // Deprecated, use state with initiallyVisible
    fadeDurationMs: Int = 200,
    animationSpec: AnimationSpec<Float> = tween(durationMillis = fadeDurationMs, easing = EaseInOut),
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
        state = state,
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
    state: TrayAppState? = null,
    windowSize: DpSize? = null, // Deprecated, use state.windowSize
    visibleOnStart: Boolean = false, // Deprecated, use state with initiallyVisible
    fadeDurationMs: Int = 200,
    animationSpec: AnimationSpec<Float> = tween(durationMillis = fadeDurationMs, easing = EaseInOut),
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
        state = state,
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
    state: TrayAppState? = null,
    windowSize: DpSize? = null, // Deprecated, use state.windowSize
    visibleOnStart: Boolean = false, // Deprecated, use state with initiallyVisible
    fadeDurationMs: Int = 200,
    animationSpec: AnimationSpec<Float> = tween(durationMillis = fadeDurationMs, easing = EaseInOut),
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
            state = state,
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
            state = state,
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
    state: TrayAppState? = null,
    windowSize: DpSize? = null, // Deprecated, use state.windowSize
    visibleOnStart: Boolean = false, // Deprecated, use state with initiallyVisible
    fadeDurationMs: Int = 200,
    animationSpec: AnimationSpec<Float> = tween(durationMillis = fadeDurationMs, easing = EaseInOut),
    menu: (TrayMenuBuilder.() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    // Convert DrawableResource to Painter and delegate to Painter overload
    val painter = painterResource(icon)
    TrayApp(
        icon = painter,
        iconRenderProperties = iconRenderProperties,
        tooltip = tooltip,
        state = state,
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
    state: TrayAppState? = null,
    windowSize: DpSize? = null, // Deprecated, use state.windowSize
    visibleOnStart: Boolean = false, // Deprecated, use state with initiallyVisible
    fadeDurationMs: Int = 200,
    animationSpec: AnimationSpec<Float> = tween(durationMillis = fadeDurationMs, easing = EaseInOut),
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
            state = state,
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
            state = state,
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
 * TrayApp overload that uses a composable icon and **keeps the popup window mounted**.
 *
 * Key changes vs your previous version:
 * - The DialogWindow is **always part of the composition**.
 * - We toggle visibility with `visible = shouldShowWindow` and animate alpha.
 * - We update `dialogState.position` on each show so the first frame renders at the
 *   right spot (no jump-to-corner on first show).
 * - Focus/outside-click watchers are attached only while visible.
 */
@ExperimentalTrayAppApi
@Composable
fun ApplicationScope.TrayApp(
    iconContent: @Composable () -> Unit,
    iconRenderProperties: IconRenderProperties = IconRenderProperties.forCurrentOperatingSystem(),
    tooltip: String,
    state: TrayAppState? = null,
    windowSize: DpSize? = null, // Deprecated, use state.windowSize
    visibleOnStart: Boolean = false, // Deprecated, use state with initiallyVisible
    fadeDurationMs: Int = 200,
    animationSpec: AnimationSpec<Float> = tween(durationMillis = fadeDurationMs, easing = EaseInOut),
    menu: (TrayMenuBuilder.() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    // Create or use provided state
    val trayAppState = state ?: rememberTrayAppState(
        initialWindowSize = windowSize ?: DpSize(300.dp, 200.dp),
        initiallyVisible = visibleOnStart,
        // Default remains AUTO to keep backward compatibility
        initialDismissMode = TrayWindowDismissMode.AUTO
    )

    // Collect state flows
    val isVisible by trayAppState.isVisible.collectAsState()
    val currentWindowSize by trayAppState.windowSize.collectAsState()
    val dismissMode by trayAppState.dismissMode.collectAsState()

    var shouldShowWindow by remember { mutableStateOf(false) }

    // Update window size in state if provided through parameter (for backward compatibility)
    LaunchedEffect(windowSize) {
        windowSize?.let { trayAppState.setWindowSize(it) }
    }

    val isDark = isMenuBarInDarkMode()
    val os = getOperatingSystem()

    // Debounce + stability
    var lastPrimaryActionAt by remember { mutableStateOf(0L) }
    val toggleDebounceMs = 280L

    var lastShownAt by remember { mutableStateOf(0L) }
    var lastHiddenAt by remember { mutableStateOf(0L) }
    val minVisibleDurationMs = 350L
    val minHiddenDurationMs = 250L

    // Opacity animation
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

    // Windows-specific guards
    var lastFocusLostAt by remember { mutableStateOf(0L) }
    var autoHideEnabledAt by remember { mutableStateOf(0L) }

    // Keep a persistent dialog state (mounted once)
    val dialogState = rememberDialogState(size = currentWindowSize)

    // Update dialog state when window size changes
    LaunchedEffect(currentWindowSize) {
        dialogState.size = currentWindowSize
    }

    // Helper to request hide with a minimum visible duration guard.
    // NOTE: This is used for explicit hide (tray click toggle or programmatic hide).
    val requestHideExplicit: () -> Unit = {
        val now = System.currentTimeMillis()
        val sinceShow = now - lastShownAt
        if (sinceShow >= minVisibleDurationMs) {
            trayAppState.hide()
            lastHiddenAt = System.currentTimeMillis()
        } else {
            val wait = minVisibleDurationMs - sinceShow
            CoroutineScope(Dispatchers.IO).launch {
                delay(wait)
                trayAppState.hide()
                lastHiddenAt = System.currentTimeMillis()
            }
        }
    }

    // Primary action: toggle visibility with debounce + platform guard
    val internalPrimaryAction: () -> Unit = {
        val now = System.currentTimeMillis()
        if (now - lastPrimaryActionAt >= toggleDebounceMs) {
            lastPrimaryActionAt = now

            if (isVisible) {
                // Explicit hide (works both in AUTO and MANUAL)
                requestHideExplicit()
            } else {
                if (now - lastHiddenAt >= minHiddenDurationMs) {
                    if (os == WINDOWS && (now - lastFocusLostAt) < 300) {
                        // Ignore immediate re-show after focus loss on Windows
                    } else {
                        trayAppState.show()
                        // `lastShownAt` will be set in LaunchedEffect(isVisible)
                    }
                }
            }
        }
    }

    // Consolidated visibility handling with position calculation BEFORE showing the window.
    LaunchedEffect(isVisible) {
        if (isVisible) {
            if (!shouldShowWindow) {
                delay(150)

                val widthPx = currentWindowSize.width.value.toInt()
                val heightPx = currentWindowSize.height.value.toInt()
                var position: WindowPosition = WindowPosition.PlatformDefault
                val deadline = System.currentTimeMillis() + 3000 // Max 3s wait to avoid hanging
                while (position is WindowPosition.PlatformDefault && System.currentTimeMillis() < deadline) {
                    position = getTrayWindowPositionForInstance(
                        tray.instanceKey(), widthPx, heightPx
                    )
                    delay(150)
                }
                dialogState.position = position

                if (os == WINDOWS) {
                    autoHideEnabledAt = System.currentTimeMillis() + 1000
                }

                // Now safe to show—no glitch.
                shouldShowWindow = true
                lastShownAt = System.currentTimeMillis()
            }
        } else {
            delay(fadeDurationMs.toLong())
            shouldShowWindow = false
            lastHiddenAt = System.currentTimeMillis()
        }
    }

    // Update tray icon/menu when needed
    LaunchedEffect(pngIconPath, windowsIconPath, tooltip, internalPrimaryAction, menu, contentHash, menuHash) {
        tray.update(pngIconPath, windowsIconPath, tooltip, internalPrimaryAction, menu)
    }

    // macOS: manage Dock visibility based on global AWT visibility
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

    DisposableEffect(Unit) { onDispose { tray.dispose() } }

    // Invisible helper window (Compose requirement on some platforms)
    DialogWindow(
        onCloseRequest = { /* noop */ },
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

    // === Main popup window (ALWAYS MOUNTED) ===
    DialogWindow(
        // Closing the popup via OS/ESC is considered explicit user intent → allowed in MANUAL
        onCloseRequest = { requestHideExplicit() },
        title = "",
        undecorated = true,
        resizable = false,
        focusable = true,
        alwaysOnTop = true,
        transparent = true,
        visible = shouldShowWindow,
        state = dialogState,
    ) {
        // Attach/Detach platform listeners only while window is visible OR when mode changes.
        // Including dismissMode in the key ensures watchers reconfigure when switching AUTO <-> MANUAL.
        DisposableEffect(shouldShowWindow, dismissMode) {
            if (!shouldShowWindow) {
                onDispose { }
                return@DisposableEffect onDispose { }
            }

            // Mark this as the tray popup (macOS visibility monitor)
            try {
                window.name = WindowVisibilityMonitor.TRAY_DIALOG_NAME
            } catch (_: Throwable) { }
            runCatching { WindowVisibilityMonitor.recompute() }

            // Bring to front on open
            invokeLater {
                runCatching {
                    window.toFront()
                    window.requestFocus()
                    window.requestFocusInWindow()
                }
            }

            // Focus listener: auto-hide only if AUTO mode
            val focusListener = object : WindowFocusListener {
                override fun windowGainedFocus(e: WindowEvent?) = Unit
                override fun windowLostFocus(e: WindowEvent?) {
                    lastFocusLostAt = System.currentTimeMillis()
                    if (os == WINDOWS && lastFocusLostAt < autoHideEnabledAt) return
                    if (dismissMode == TrayWindowDismissMode.AUTO) {
                        // Auto dismiss on focus loss
                        requestHideExplicit()
                    }
                }
            }

            // Outside click watchers: start them only in AUTO mode
            val macWatcher = if (dismissMode == TrayWindowDismissMode.AUTO && getOperatingSystem() == MACOS) {
                MacOutsideClickWatcher(
                    windowSupplier = { window },
                    onOutsideClick = { invokeLater { requestHideExplicit() } }
                ).also { it.start() }
            } else null

            val linuxWatcher = if (dismissMode == TrayWindowDismissMode.AUTO && getOperatingSystem() == OperatingSystem.LINUX) {
                LinuxOutsideClickWatcher(
                    windowSupplier = { window },
                    onOutsideClick = { invokeLater { requestHideExplicit() } }
                ).also { it.start() }
            } else null

            val windowsWatcher = if (dismissMode == TrayWindowDismissMode.AUTO && getOperatingSystem() == WINDOWS) {
                WindowsOutsideClickWatcher(
                    windowSupplier = { window },
                    onOutsideClick = { invokeLater { requestHideExplicit() } }
                ).also { it.start() }
            } else null

            window.addWindowFocusListener(focusListener)

            onDispose {
                window.removeWindowFocusListener(focusListener)
                macWatcher?.stop()
                linuxWatcher?.stop()
                windowsWatcher?.stop()
                runCatching { WindowVisibilityMonitor.recompute() }
            }
        }

        // Content wrapper with fade animation (state is preserved across show/hide)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(alpha)
        ) { content() }
    }
}
