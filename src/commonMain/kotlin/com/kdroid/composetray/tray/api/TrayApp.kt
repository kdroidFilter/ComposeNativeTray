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
 * TrayApp: High-level API that creates a system tray icon with an undecorated popup window
 * that can be controlled via TrayAppState.
 *
 * This overload accepts an ImageVector with optional tint that adapts to the system theme.
 *
 * @param state TrayAppState for controlling window visibility and size
 * @param icon ImageVector for the tray icon
 * @param tint Optional color tint for the icon (defaults to system theme)
 * @param iconRenderProperties Properties for rendering the icon to tray-compatible bitmaps
 * @param tooltip Tooltip text shown on tray icon hover
 * @param fadeDurationMs Duration of fade in/out animation in milliseconds
 * @param animationSpec Animation specification for fade effects
 * @param menu Optional context menu builder for the tray icon
 * @param content Composable content displayed inside the popup window
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
    // Create icon content with adaptive tinting based on system theme
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

    // Delegate to core implementation
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
 * TrayApp overload that accepts a Painter as tray icon.
 * Suitable for raster images or custom painted icons.
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
 * TrayApp overload that accepts platform-specific icon types.
 * Allows different icons for Windows (Painter) and Mac/Linux (ImageVector).
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

    // Use platform-specific icon
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
 * TrayApp overload that accepts a DrawableResource directly.
 * Convenient for using resources from the resources folder.
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

/**
 * TrayApp overload with platform-specific resources.
 * Windows uses a DrawableResource, Mac/Linux use an ImageVector.
 */
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
 * This is the main implementation that all other overloads delegate to.
 *
 * Key features:
 * - Creates a system tray icon with customizable appearance
 * - Manages a popup window that preserves ALL states (including remember values)
 * - Handles platform-specific behaviors (Windows, macOS, Linux)
 * - Provides smooth fade animations
 * - Manages focus and outside-click behaviors
 * - Uses a hybrid approach: window stays mounted but is moved off-screen when hidden
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
    // Collect state flows from TrayAppState
    val isVisible by state.isVisible.collectAsState()
    val windowSize by state.windowSize.collectAsState()

    // Internal state for managing window display with animation
    var shouldShowWindow by remember { mutableStateOf(false) }

    // Track if window has been created at least once
    var windowCreated by remember { mutableStateOf(false) }

    // Initial position for the dialog state, calculated for initial visible
    var initialPosition by remember { mutableStateOf(WindowPosition(-10000.dp, -10000.dp)) }

    // System information
    val isDark = isMenuBarInDarkMode()
    val os = getOperatingSystem()

    // Debounce control for primary action (prevents rapid toggles)
    var lastPrimaryActionAt by remember { mutableStateOf(0L) }
    val toggleDebounceMs = 280L

    // Timing controls for minimum visible/hidden durations
    var lastShownAt by remember { mutableStateOf(0L) }
    var lastHiddenAt by remember { mutableStateOf(0L) }
    val minVisibleDurationMs = 350L  // Minimum time window stays visible
    val minHiddenDurationMs = 250L   // Minimum time window stays hidden

    // Animated opacity for smooth fade in/out
    val alpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = animationSpec,
        label = "window_fade"
    )

    // Generate hashes for detecting icon and menu changes
    val contentHash = ComposableIconUtils.calculateContentHash(iconRenderProperties, iconContent) +
            isDark.hashCode()
    val menuHash = MenuContentHash.calculateMenuHash(menu)

    // Render icon to platform-specific formats
    val pngIconPath = remember(contentHash) {
        ComposableIconUtils.renderComposableToPngFile(iconRenderProperties, iconContent)
    }
    val windowsIconPath = remember(contentHash) {
        if (os == WINDOWS)
            ComposableIconUtils.renderComposableToIcoFile(iconRenderProperties, iconContent)
        else pngIconPath
    }

    // Native tray instance
    val tray = remember { NativeTray() }

    // Focus tracking for Windows-specific behavior
    var lastFocusLostAt by remember { mutableStateOf(0L) }
    var autoHideEnabledAt by remember { mutableStateOf(0L) }

    // Helper function to request hide with minimum visible duration guard
    val requestHide: () -> Unit = {
        val now = System.currentTimeMillis()
        val sinceShow = now - lastShownAt

        if (sinceShow >= minVisibleDurationMs) {
            // Enough time has passed, hide immediately
            state.hide()
            lastHiddenAt = System.currentTimeMillis()
        } else {
            // Schedule hide after minimum duration
            val wait = minVisibleDurationMs - sinceShow
            CoroutineScope(Dispatchers.IO).launch {
                delay(wait)
                state.hide()
                lastHiddenAt = System.currentTimeMillis()
            }
        }
    }

    // Primary action handler for tray icon clicks
    val internalPrimaryAction: () -> Unit = {
        val now = System.currentTimeMillis()

        // Apply debounce to prevent rapid toggles
        if (now - lastPrimaryActionAt >= toggleDebounceMs) {
            lastPrimaryActionAt = now

            if (isVisible) {
                // Window is visible, request hide with timing guard
                val sinceShow = now - lastShownAt
                if (sinceShow >= minVisibleDurationMs) {
                    state.hide()
                    lastHiddenAt = System.currentTimeMillis()
                } else {
                    // Schedule hide after minimum visible duration
                    val wait = minVisibleDurationMs - sinceShow
                    CoroutineScope(Dispatchers.IO).launch {
                        delay(wait)
                        state.hide()
                        lastHiddenAt = System.currentTimeMillis()
                    }
                }
            } else {
                // Window is hidden, show if enough time has passed
                if (now - lastHiddenAt >= minHiddenDurationMs) {
                    // Windows-specific: ignore if focus was just lost
                    if (os == WINDOWS && (now - lastFocusLostAt) < 300) {
                        // Ignore click - too soon after focus loss
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
            // Show window immediately
            shouldShowWindow = true
            if (!windowCreated) {
                windowCreated = true
            }
            lastShownAt = System.currentTimeMillis()
        } else {
            // Hide window after fade animation completes
            delay(fadeDurationMs.toLong())
            shouldShowWindow = false
            lastHiddenAt = System.currentTimeMillis()
        }
    }

    // Update tray icon when properties change
    LaunchedEffect(pngIconPath, windowsIconPath, tooltip, internalPrimaryAction, menu, contentHash, menuHash) {
        tray.update(pngIconPath, windowsIconPath, tooltip, internalPrimaryAction, menu)
    }

    // macOS-specific: Manage Dock visibility based on window state
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

    // Handle initial visibility setup
    LaunchedEffect(Unit) {
        if (isVisible) {
            // macOS: Small delay for system readiness
            if (os == MACOS) {
                delay(100)
            }

            // Windows: Wait for tray position to be available
            if (os == WINDOWS) {
                val deadline = System.currentTimeMillis() + 2000
                val key = tray.instanceKey()
                while (TrayClickTracker.getLastClickPosition(key) == null &&
                    System.currentTimeMillis() < deadline) {
                    delay(50)
                }
                // Delay auto-hide to prevent immediate hiding on startup
                autoHideEnabledAt = System.currentTimeMillis() + 1000
            }

            // Calculate initial position after delays
            val widthPx = windowSize.width.value.toInt()
            val heightPx = windowSize.height.value.toInt()
            initialPosition = getTrayWindowPositionForInstance(
                tray.instanceKey(),
                widthPx,
                heightPx
            ) as WindowPosition.Absolute

            shouldShowWindow = true
            windowCreated = true
            lastShownAt = System.currentTimeMillis()
        }
    }

    // Clean up tray icon on disposal
    DisposableEffect(Unit) {
        onDispose { tray.dispose() }
    }

    // Invisible helper window (required for some platform behaviors)
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

    // Main popup window - always mounted once created to preserve states
    // Uses hybrid approach: moves off-screen instead of unmounting
    if (windowCreated) {
        // Use the pre-calculated initial position
        val dialogState = rememberDialogState(
            position = initialPosition,
            size = windowSize
        )

        DialogWindow(
            onCloseRequest = { requestHide() },
            title = "",
            undecorated = true,
            resizable = false,
            focusable = shouldShowWindow,  // Only focusable when visible
            alwaysOnTop = shouldShowWindow,  // Only on top when visible
            transparent = true,
            visible = true,  // Always visible to the system
            state = dialogState
        ) {
            DisposableEffect(Unit) {
                // Set window name for monitoring
                try { window.name = WindowVisibilityMonitor.TRAY_DIALOG_NAME } catch (_: Throwable) {}

                // Focus listener for auto-hide on focus loss
                val focusListener = object : WindowFocusListener {
                    override fun windowGainedFocus(e: WindowEvent?) = Unit
                    override fun windowLostFocus(e: WindowEvent?) {
                        lastFocusLostAt = System.currentTimeMillis()
                        // Windows: Ignore focus loss during startup period
                        if (os == WINDOWS && lastFocusLostAt < autoHideEnabledAt) {
                            return
                        }
                        if (shouldShowWindow) {  // Only hide if currently visible
                            requestHide()
                        }
                    }
                }

                // Platform-specific outside click detection
                val macWatcher = if (getOperatingSystem() == MACOS) {
                    MacOutsideClickWatcher(
                        windowSupplier = { window },
                        onOutsideClick = {
                            if (shouldShowWindow) {  // Only hide if currently visible
                                invokeLater { requestHide() }
                            }
                        }
                    ).also { it.start() }
                } else null

                val linuxWatcher = if (getOperatingSystem() == OperatingSystem.LINUX) {
                    LinuxOutsideClickWatcher(
                        windowSupplier = { window },
                        onOutsideClick = {
                            if (shouldShowWindow) {  // Only hide if currently visible
                                invokeLater { requestHide() }
                            }
                        }
                    ).also { it.start() }
                } else null

                window.addWindowFocusListener(focusListener)

                // Cleanup on disposal
                onDispose {
                    window.removeWindowFocusListener(focusListener)
                    macWatcher?.stop()
                    linuxWatcher?.stop()
                }
            }

            // React to visibility changes and reposition window
            // IMPORTANT: Position is recalculated each time window shows because:
            // - The tray icon might have moved (user moved taskbar, changed resolution, etc.)
            // - Window size might have changed
            LaunchedEffect(shouldShowWindow, windowSize) {
                if (shouldShowWindow) {
                    // Recalculate position when showing (tray icon might have moved)
                    // or when window size changes
                    val widthPx = windowSize.width.value.toInt()
                    val heightPx = windowSize.height.value.toInt()
                    val newPosition = getTrayWindowPositionForInstance(
                        tray.instanceKey(),
                        widthPx,
                        heightPx
                    )
                    dialogState.position = newPosition

                    // Update size if changed
                    dialogState.size = windowSize

                    // Window is becoming visible
                    runCatching { WindowVisibilityMonitor.recompute() }
                    invokeLater {
                        try {
                            // Bring window to front and request focus
                            window.toFront()
                            window.requestFocus()
                            window.requestFocusInWindow()
                        } catch (_: Throwable) {
                        }
                    }
                } else {
                    // Move window off-screen when hiding
                    dialogState.position = WindowPosition(-10000.dp, -10000.dp)

                    // Window is becoming hidden
                    runCatching { WindowVisibilityMonitor.recompute() }
                }
            }

            // Content wrapper with fade animation
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(alpha)  // Apply fade animation
            ) {
                // Content is always mounted, preserving all states
                content()
            }
        }
    }
}