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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
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
import io.github.kdroidfilter.platformtools.getOperatingSystem
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import java.awt.EventQueue.invokeLater
import java.awt.event.WindowEvent
import java.awt.event.WindowFocusListener

/**
 * Creates a system tray icon with a popup window.
 * The window's visibility is controlled by the tray icon and the dismiss mode.
 */
@ExperimentalTrayAppApi
@Composable
fun ApplicationScope.TrayApp(
    icon: ImageVector,
    tint: Color? = null,
    iconRenderProperties: IconRenderProperties = IconRenderProperties.forCurrentOperatingSystem(),
    tooltip: String,
    state: TrayAppState = rememberTrayAppState(),
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
            colorFilter = tint?.let { ColorFilter.tint(it) }
                ?: if (isDark) ColorFilter.tint(Color.White)
                else ColorFilter.tint(Color.Black)
        )
    }

    TrayAppInternal(
        iconContent = iconContent,
        iconRenderProperties = iconRenderProperties,
        tooltip = tooltip,
        state = state,
        fadeDurationMs = fadeDurationMs,
        animationSpec = animationSpec,
        menu = menu,
        content = content,
    )
}

@ExperimentalTrayAppApi
@Composable
fun ApplicationScope.TrayApp(
    icon: Painter,
    iconRenderProperties: IconRenderProperties = IconRenderProperties.forCurrentOperatingSystem(),
    tooltip: String,
    state: TrayAppState = rememberTrayAppState(),
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

    TrayAppInternal(
        iconContent = iconContent,
        iconRenderProperties = iconRenderProperties,
        tooltip = tooltip,
        state = state,
        fadeDurationMs = fadeDurationMs,
        animationSpec = animationSpec,
        menu = menu,
        content = content,
    )
}

@ExperimentalTrayAppApi
@Composable
fun ApplicationScope.TrayApp(
    icon: DrawableResource,
    iconRenderProperties: IconRenderProperties = IconRenderProperties.forCurrentOperatingSystem(),
    tooltip: String,
    state: TrayAppState = rememberTrayAppState(),
    fadeDurationMs: Int = 200,
    animationSpec: AnimationSpec<Float> = tween(durationMillis = fadeDurationMs, easing = EaseInOut),
    menu: (TrayMenuBuilder.() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val painter = painterResource(icon)
    TrayApp(
        icon = painter,
        iconRenderProperties = iconRenderProperties,
        tooltip = tooltip,
        state = state,
        fadeDurationMs = fadeDurationMs,
        animationSpec = animationSpec,
        menu = menu,
        content = content,
    )
}

@ExperimentalTrayAppApi
@Composable
fun ApplicationScope.TrayApp(
    windowsIcon: Painter,
    macLinuxIcon: ImageVector,
    tint: Color? = null,
    iconRenderProperties: IconRenderProperties = IconRenderProperties.forCurrentOperatingSystem(),
    tooltip: String,
    state: TrayAppState = rememberTrayAppState(),
    fadeDurationMs: Int = 200,
    animationSpec: AnimationSpec<Float> = tween(durationMillis = fadeDurationMs, easing = EaseInOut),
    menu: (TrayMenuBuilder.() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    when (getOperatingSystem()) {
        OperatingSystem.WINDOWS -> TrayApp(
            icon = windowsIcon,
            iconRenderProperties = iconRenderProperties,
            tooltip = tooltip,
            state = state,
            fadeDurationMs = fadeDurationMs,
            animationSpec = animationSpec,
            menu = menu,
            content = content,
        )
        else -> TrayApp(
            icon = macLinuxIcon,
            tint = tint,
            iconRenderProperties = iconRenderProperties,
            tooltip = tooltip,
            state = state,
            fadeDurationMs = fadeDurationMs,
            animationSpec = animationSpec,
            menu = menu,
            content = content,
        )
    }
}

@ExperimentalTrayAppApi
@Composable
fun ApplicationScope.TrayApp(
    windowsIcon: DrawableResource,
    macLinuxIcon: ImageVector,
    tint: Color? = null,
    iconRenderProperties: IconRenderProperties = IconRenderProperties.forCurrentOperatingSystem(),
    tooltip: String,
    state: TrayAppState = rememberTrayAppState(),
    fadeDurationMs: Int = 200,
    animationSpec: AnimationSpec<Float> = tween(durationMillis = fadeDurationMs, easing = EaseInOut),
    menu: (TrayMenuBuilder.() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    when (getOperatingSystem()) {
        OperatingSystem.WINDOWS -> TrayApp(
            icon = windowsIcon,
            iconRenderProperties = iconRenderProperties,
            tooltip = tooltip,
            state = state,
            fadeDurationMs = fadeDurationMs,
            animationSpec = animationSpec,
            menu = menu,
            content = content,
        )
        else -> TrayApp(
            icon = macLinuxIcon,
            tint = tint,
            iconRenderProperties = iconRenderProperties,
            tooltip = tooltip,
            state = state,
            fadeDurationMs = fadeDurationMs,
            animationSpec = animationSpec,
            menu = menu,
            content = content,
        )
    }
}

/**
 * Internal implementation of TrayApp with composable icon content.
 * Keeps the window mounted to preserve state.
 */
@ExperimentalTrayAppApi
@Composable
private fun ApplicationScope.TrayAppInternal(
    iconContent: @Composable () -> Unit,
    iconRenderProperties: IconRenderProperties,
    tooltip: String,
    state: TrayAppState,
    fadeDurationMs: Int,
    animationSpec: AnimationSpec<Float>,
    menu: (TrayMenuBuilder.() -> Unit)?,
    content: @Composable () -> Unit,
) {
    val isVisible by state.isVisible.collectAsState()
    val currentWindowSize by state.windowSize.collectAsState()
    val dismissMode by state.dismissMode.collectAsState()

    var shouldShowWindow by remember { mutableStateOf(false) }
    val os = getOperatingSystem()
    val isDark = isMenuBarInDarkMode()

    // Animation
    val alpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = animationSpec,
        label = "window_fade"
    )

    // Icon rendering
    val contentHash = ComposableIconUtils.calculateContentHash(iconRenderProperties, iconContent) +
            isDark.hashCode()
    val menuHash = MenuContentHash.calculateMenuHash(menu)

    val pngIconPath = remember(contentHash) {
        ComposableIconUtils.renderComposableToPngFile(iconRenderProperties, iconContent)
    }

    val windowsIconPath = remember(contentHash) {
        if (os == OperatingSystem.WINDOWS) {
            ComposableIconUtils.renderComposableToIcoFile(iconRenderProperties, iconContent)
        } else {
            pngIconPath
        }
    }

    // Native tray
    val tray = remember { NativeTray() }

    // Primary action: toggle visibility
    val primaryAction: () -> Unit = {
        state.toggle()
    }

    // Dialog state (persistent)
    val dialogState = rememberDialogState(size = currentWindowSize)

    // Update dialog state when window size changes
    LaunchedEffect(currentWindowSize) {
        dialogState.size = currentWindowSize
    }

    // Handle visibility changes
    LaunchedEffect(isVisible) {
        if (isVisible) {
            if (!shouldShowWindow) {
                // Calculate position before showing
                delay(150)
                val widthPx = currentWindowSize.width.value.toInt()
                val heightPx = currentWindowSize.height.value.toInt()

                var position: WindowPosition = WindowPosition.PlatformDefault
                val deadline = System.currentTimeMillis() + 3000

                while (position is WindowPosition.PlatformDefault &&
                    System.currentTimeMillis() < deadline) {
                    position = getTrayWindowPositionForInstance(
                        tray.instanceKey(), widthPx, heightPx
                    )
                    delay(150)
                }

                dialogState.position = position
                shouldShowWindow = true
            }
        } else {
            // Wait for fade animation to complete
            delay(fadeDurationMs.toLong())
            shouldShowWindow = false
        }
    }

    // Update tray icon and menu
    LaunchedEffect(pngIconPath, windowsIconPath, tooltip, primaryAction, menu, contentHash, menuHash) {
        tray.update(pngIconPath, windowsIconPath, tooltip, primaryAction, menu)
    }

    // macOS: Manage Dock visibility
    if (os == OperatingSystem.MACOS) {
        LaunchedEffect(Unit) {
            WindowVisibilityMonitor.hasAnyVisibleWindows.collectLatest { hasVisible ->
                runCatching {
                    val manager = MacOSWindowManager()
                    if (hasVisible) {
                        manager.showInDock()
                    } else {
                        manager.hideFromDock()
                    }
                }
            }
        }
    }

    // Cleanup
    DisposableEffect(Unit) {
        onDispose {
            tray.dispose()
        }
    }

    // Invisible helper window (required on some platforms)
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

    // Main popup window (always mounted to preserve state)
    DialogWindow(
        onCloseRequest = {
            if (dismissMode == TrayWindowDismissMode.MANUAL) {
                state.hide()
            }
        },
        title = "",
        undecorated = true,
        resizable = false,
        focusable = true,
        alwaysOnTop = true,
        transparent = true,
        visible = shouldShowWindow,
        state = dialogState,
    ) {
        // Setup auto-dismiss listeners only if in AUTO mode and window is visible
        DisposableEffect(shouldShowWindow, dismissMode) {
            if (!shouldShowWindow) {
                return@DisposableEffect onDispose { }
            }

            // Mark as tray dialog for macOS
            try {
                window.name = WindowVisibilityMonitor.TRAY_DIALOG_NAME
            } catch (_: Throwable) { }

            runCatching { WindowVisibilityMonitor.recompute() }

            // Bring window to front
            invokeLater {
                try {
                    window.toFront()
                    window.requestFocus()
                    window.requestFocusInWindow()
                } catch (_: Throwable) { }
            }

            // Only attach auto-dismiss listeners if in AUTO mode
            if (dismissMode == TrayWindowDismissMode.AUTO) {
                val focusListener = object : WindowFocusListener {
                    override fun windowGainedFocus(e: WindowEvent?) = Unit
                    override fun windowLostFocus(e: WindowEvent?) {
                        invokeLater { state.hide() }
                    }
                }

                val macWatcher = if (os == OperatingSystem.MACOS) {
                    MacOutsideClickWatcher(
                        windowSupplier = { window },
                        onOutsideClick = { invokeLater { state.hide() } }
                    ).also { it.start() }
                } else null

                val linuxWatcher = if (os == OperatingSystem.LINUX) {
                    LinuxOutsideClickWatcher(
                        windowSupplier = { window },
                        onOutsideClick = { invokeLater { state.hide() } }
                    ).also { it.start() }
                } else null

                window.addWindowFocusListener(focusListener)

                onDispose {
                    window.removeWindowFocusListener(focusListener)
                    macWatcher?.stop()
                    linuxWatcher?.stop()
                    runCatching { WindowVisibilityMonitor.recompute() }
                }
            } else {
                // MANUAL mode: no auto-dismiss listeners
                onDispose {
                    runCatching { WindowVisibilityMonitor.recompute() }
                }
            }
        }

        // Content with fade animation
        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(alpha)
        ) {
            content()
        }
    }
}