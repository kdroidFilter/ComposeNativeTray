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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.awt.EventQueue.invokeLater
import java.awt.event.WindowEvent
import java.awt.event.WindowFocusListener

/**
 * TrayApp: High-level API that creates a system tray icon and an undecorated popup window
 * that can be controlled via TrayAppState.
 *
 * Parameters:
 * - state: TrayAppState for controlling window visibility and size
 * - icon: ImageVector for the tray icon (with optional tint that adapts)
 * - iconRenderProperties: Properties for rendering the icon to tray-compatible bitmaps
 * - tooltip: Tooltip text shown on tray icon hover
 * - fadeDurationMs: Duration of fade in/out animation in milliseconds
 * - animationSpec: Animation specification for fade effects
 * - menu: Optional context menu for the tray icon
 * - content: Composable content displayed inside the popup window
 */
@ExperimentalTrayAppApi
@Composable
fun ApplicationScope.TrayApp(
    state: TrayAppState,
    icon: ImageVector,
    tint: Color? = null,
    iconRenderProperties: IconRenderProperties = IconRenderProperties.forCurrentOperatingSystem(),
    tooltip: String,
    fadeDurationMs: Int = 200,
    animationSpec: AnimationSpec<Float> = tween(durationMillis = fadeDurationMs, easing = EaseInOut),
    menu: (TrayMenuBuilder.() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
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
        state = state,
        iconContent = iconContent,
        iconRenderProperties = iconRenderProperties,
        tooltip = tooltip,
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
    state: TrayAppState,
    icon: Painter,
    iconRenderProperties: IconRenderProperties = IconRenderProperties.forCurrentOperatingSystem(),
    tooltip: String,
    fadeDurationMs: Int = 200,
    animationSpec: AnimationSpec<Float> = tween(durationMillis = fadeDurationMs, easing = EaseInOut),
    menu: (TrayMenuBuilder.() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val iconContent: @Composable () -> Unit = {
        Image(
            painter = icon,
            contentDescription = null,
            modifier = Modifier.fillMaxSize()
        )
    }
    TrayApp(
        state = state,
        iconContent = iconContent,
        iconRenderProperties = iconRenderProperties,
        tooltip = tooltip,
        fadeDurationMs = fadeDurationMs,
        animationSpec = animationSpec,
        content = content,
        menu = menu,
    )
}

/**
 * TrayApp overload: accepts platform-specific icon types.
 */
@ExperimentalTrayAppApi
@Composable
fun ApplicationScope.TrayApp(
    state: TrayAppState,
    windowsIcon: Painter,
    macLinuxIcon: ImageVector,
    tint: Color? = null,
    iconRenderProperties: IconRenderProperties = IconRenderProperties.forCurrentOperatingSystem(),
    tooltip: String,
    fadeDurationMs: Int = 200,
    animationSpec: AnimationSpec<Float> = tween(durationMillis = fadeDurationMs, easing = EaseInOut),
    menu: (TrayMenuBuilder.() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val os = getOperatingSystem()
    if (os == WINDOWS) {
        TrayApp(
            state = state,
            icon = windowsIcon,
            iconRenderProperties = iconRenderProperties,
            tooltip = tooltip,
            fadeDurationMs = fadeDurationMs,
            animationSpec = animationSpec,
            menu = menu,
            content = content,
        )
    } else {
        TrayApp(
            state = state,
            icon = macLinuxIcon,
            tint = tint,
            iconRenderProperties = iconRenderProperties,
            tooltip = tooltip,
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
    state: TrayAppState,
    icon: DrawableResource,
    iconRenderProperties: IconRenderProperties = IconRenderProperties.forCurrentOperatingSystem(),
    tooltip: String,
    fadeDurationMs: Int = 200,
    animationSpec: AnimationSpec<Float> = tween(durationMillis = fadeDurationMs, easing = EaseInOut),
    menu: (TrayMenuBuilder.() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val painter = painterResource(icon)
    TrayApp(
        state = state,
        icon = painter,
        iconRenderProperties = iconRenderProperties,
        tooltip = tooltip,
        fadeDurationMs = fadeDurationMs,
        animationSpec = animationSpec,
        menu = menu,
        content = content,
    )
}

@ExperimentalTrayAppApi
@Composable
fun ApplicationScope.TrayApp(
    state: TrayAppState,
    windowsIcon: DrawableResource,
    macLinuxIcon: ImageVector,
    tint: Color? = null,
    iconRenderProperties: IconRenderProperties = IconRenderProperties.forCurrentOperatingSystem(),
    tooltip: String,
    fadeDurationMs: Int = 200,
    animationSpec: AnimationSpec<Float> = tween(durationMillis = fadeDurationMs, easing = EaseInOut),
    menu: (TrayMenuBuilder.() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val os = getOperatingSystem()
    if (os == WINDOWS) {
        val painter = painterResource(windowsIcon)
        TrayApp(
            state = state,
            icon = painter,
            iconRenderProperties = iconRenderProperties,
            tooltip = tooltip,
            fadeDurationMs = fadeDurationMs,
            animationSpec = animationSpec,
            menu = menu,
            content = content,
        )
    } else {
        TrayApp(
            state = state,
            icon = macLinuxIcon,
            tint = tint,
            iconRenderProperties = iconRenderProperties,
            tooltip = tooltip,
            fadeDurationMs = fadeDurationMs,
            animationSpec = animationSpec,
            menu = menu,
            content = content,
        )
    }
}

/**
 * Core TrayApp implementation with composable iconContent and TrayAppState.
 */
@ExperimentalTrayAppApi
@Composable
fun ApplicationScope.TrayApp(
    state: TrayAppState,
    iconContent: @Composable () -> Unit,
    iconRenderProperties: IconRenderProperties = IconRenderProperties.forCurrentOperatingSystem(),
    tooltip: String,
    fadeDurationMs: Int = 200,
    animationSpec: AnimationSpec<Float> = tween(durationMillis = fadeDurationMs, easing = EaseInOut),
    menu: (TrayMenuBuilder.() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    // Collect state from TrayAppState
    val isVisible by state.isVisible.collectAsState()
    val windowSize by state.windowSize.collectAsState()

    var shouldShowWindow by remember { mutableStateOf(false) }

    val isDark = isMenuBarInDarkMode()
    val os = getOperatingSystem()

    // Debounce and timing controls
    var lastPrimaryActionAt by remember { mutableStateOf(0L) }
    val toggleDebounceMs = 280L

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

    var lastFocusLostAt by remember { mutableStateOf(0L) }
    var autoHideEnabledAt by remember { mutableStateOf(0L) }

    // Helper to request hide with minimum visible duration guard
    val requestHide: () -> Unit = {
        val now = System.currentTimeMillis()
        val sinceShow = now - lastShownAt
        if (sinceShow >= minVisibleDurationMs) {
            state.hide()
            lastHiddenAt = System.currentTimeMillis()
        } else {
            val wait = minVisibleDurationMs - sinceShow
            CoroutineScope(Dispatchers.IO).launch {
                delay(wait)
                state.hide()
                lastHiddenAt = System.currentTimeMillis()
            }
        }
    }

    // Primary action: toggle visibility with state
    val internalPrimaryAction: () -> Unit = {
        val now = System.currentTimeMillis()
        if (now - lastPrimaryActionAt >= toggleDebounceMs) {
            lastPrimaryActionAt = now

            if (isVisible) {
                val sinceShow = now - lastShownAt
                if (sinceShow >= minVisibleDurationMs) {
                    state.hide()
                    lastHiddenAt = System.currentTimeMillis()
                } else {
                    val wait = minVisibleDurationMs - sinceShow
                    CoroutineScope(Dispatchers.IO).launch {
                        delay(wait)
                        state.hide()
                        lastHiddenAt = System.currentTimeMillis()
                    }
                }
            } else {
                if (now - lastHiddenAt >= minHiddenDurationMs) {
                    if (os == WINDOWS && (now - lastFocusLostAt) < 300) {
                        // Ignore
                    } else {
                        state.show()
                        lastShownAt = System.currentTimeMillis()
                    }
                }
            }
        }
    }

    // React to visibility changes from state
    LaunchedEffect(isVisible) {
        if (isVisible) {
            shouldShowWindow = true
            lastShownAt = System.currentTimeMillis()
        } else {
            delay(fadeDurationMs.toLong())
            shouldShowWindow = false
            lastHiddenAt = System.currentTimeMillis()
        }
    }

    LaunchedEffect(pngIconPath, windowsIconPath, tooltip, internalPrimaryAction, menu, contentHash, menuHash) {
        tray.update(pngIconPath, windowsIconPath, tooltip, internalPrimaryAction, menu)
    }

    // On macOS, automatically manage Dock visibility
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

    // Handle initial visibility
    LaunchedEffect(Unit) {
        if (isVisible) {
            if (os == MACOS) {
                delay(100)
            }
            if (os == WINDOWS) {
                val deadline = System.currentTimeMillis() + 2000
                val key = tray.instanceKey()
                while (TrayClickTracker.getLastClickPosition(key) == null &&
                    System.currentTimeMillis() < deadline) {
                    delay(50)
                }
                autoHideEnabledAt = System.currentTimeMillis() + 1000
            }
            shouldShowWindow = true
            lastShownAt = System.currentTimeMillis()
        }
    }

    DisposableEffect(Unit) {
        onDispose { tray.dispose() }
    }

    // Invisible helper window
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
                try { window.name = WindowVisibilityMonitor.TRAY_DIALOG_NAME } catch (_: Throwable) {}
                runCatching { WindowVisibilityMonitor.recompute() }
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
                            return
                        }
                        requestHide()
                    }
                }

                val macWatcher = if (getOperatingSystem() == MACOS) {
                    MacOutsideClickWatcher(
                        windowSupplier = { window },
                        onOutsideClick = { invokeLater { requestHide() } }
                    ).also { it.start() }
                } else null

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
                    runCatching { WindowVisibilityMonitor.recompute() }
                }
            }

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