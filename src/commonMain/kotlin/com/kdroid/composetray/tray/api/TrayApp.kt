@file:OptIn(
    ExperimentalTrayAppApi::class,
    ExperimentalTransitionApi::class,
    InternalAnimationApi::class
)

package com.kdroid.composetray.tray.api

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import com.kdroid.composetray.lib.linux.LinuxOutsideClickWatcher
import com.kdroid.composetray.utils.debugln
import com.kdroid.composetray.lib.mac.MacOSWindowManager
import com.kdroid.composetray.lib.mac.MacOutsideClickWatcher
import com.kdroid.composetray.lib.mac.MacTrayLoader
import com.kdroid.composetray.lib.windows.WindowsOutsideClickWatcher
import com.kdroid.composetray.tray.impl.WindowsTrayInitializer
import com.kdroid.composetray.menu.api.TrayMenuBuilder
import com.kdroid.composetray.utils.*
import io.github.kdroidfilter.platformtools.LinuxDesktopEnvironment
import io.github.kdroidfilter.platformtools.OperatingSystem
import io.github.kdroidfilter.platformtools.OperatingSystem.MACOS
import io.github.kdroidfilter.platformtools.OperatingSystem.WINDOWS
import io.github.kdroidfilter.platformtools.detectLinuxDesktopEnvironment
import io.github.kdroidfilter.platformtools.getOperatingSystem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import java.awt.EventQueue.invokeLater
import java.awt.event.WindowEvent
import java.awt.event.WindowFocusListener

// --------------------- Public API (defaults) ---------------------

private val defaultTrayAppEnterTransition =
    if (getOperatingSystem() == WINDOWS)
        slideInVertically(
            initialOffsetY = { fullHeight -> fullHeight },
            animationSpec = tween(250, easing = EaseInOut)
        ) + fadeIn(animationSpec = tween(200, easing = EaseInOut))
    else
        fadeIn(animationSpec = tween(if (detectLinuxDesktopEnvironment() == LinuxDesktopEnvironment.KDE) 50 else 200, easing = EaseInOut))

private val defaultTrayAppExitTransition =
    if (getOperatingSystem() == WINDOWS)
        slideOutVertically(
            targetOffsetY = { fullHeight -> fullHeight },
            animationSpec = tween(250, easing = EaseInOut)
        ) + fadeOut(animationSpec = tween(200, easing = EaseInOut))
    else
        fadeOut(animationSpec = tween(if (detectLinuxDesktopEnvironment() == LinuxDesktopEnvironment.KDE) 50 else 200, easing = EaseInOut))

private val defaultVerticalOffset = when (getOperatingSystem()) {
    WINDOWS -> -10
    MACOS -> 5
    else -> when (detectLinuxDesktopEnvironment()) {
        LinuxDesktopEnvironment.GNOME -> 10
        else -> 0
    }
}

// --------------------- Public API (overloads) ---------------------

@ExperimentalTrayAppApi
@Composable
fun ApplicationScope.TrayApp(
    icon: ImageVector,
    tint: Color? = null,
    iconRenderProperties: IconRenderProperties = IconRenderProperties.forCurrentOperatingSystem(),
    tooltip: String,
    state: TrayAppState? = null,
    windowSize: DpSize? = null,
    visibleOnStart: Boolean = false,
    enterTransition: EnterTransition = defaultTrayAppEnterTransition,
    exitTransition: ExitTransition = defaultTrayAppExitTransition,
    transparent: Boolean = true,
    windowsTitle: String = "",
    windowIcon: Painter? = null,
    undecorated: Boolean = true,
    resizable: Boolean = false,
    horizontalOffset: Int = 0,
    verticalOffset: Int = defaultVerticalOffset,
    onPreviewKeyEvent: (KeyEvent) -> Boolean = { false },
    onKeyEvent: (KeyEvent) -> Boolean = { false },
    menu: (TrayMenuBuilder.() -> Unit)? = null,
    content: @Composable DialogWindowScope.() -> Unit,
) {
    val iconContent: @Composable () -> Unit = {
        val isDark = isMenuBarInDarkMode()
        Image(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            colorFilter = tint?.let { ColorFilter.tint(it) }
                ?: if (isDark) ColorFilter.tint(Color.White) else ColorFilter.tint(Color.Black)
        )
    }
    TrayApp(
        iconContent = iconContent,
        iconRenderProperties = iconRenderProperties,
        tooltip = tooltip,
        state = state,
        windowSize = windowSize,
        visibleOnStart = visibleOnStart,
        enterTransition = enterTransition,
        exitTransition = exitTransition,
        transparent = transparent,
        windowsTitle = windowsTitle,
        windowIcon = windowIcon,
        undecorated = undecorated,
        resizable = resizable,
        horizontalOffset = horizontalOffset,
        verticalOffset = verticalOffset,
        onPreviewKeyEvent = onPreviewKeyEvent,
        onKeyEvent = onKeyEvent,
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
    state: TrayAppState? = null,
    windowSize: DpSize? = null,
    visibleOnStart: Boolean = false,
    enterTransition: EnterTransition = defaultTrayAppEnterTransition,
    exitTransition: ExitTransition = defaultTrayAppExitTransition,
    transparent: Boolean = true,
    windowsTitle: String = "",
    windowIcon: Painter? = null,
    undecorated: Boolean = true,
    resizable: Boolean = false,
    horizontalOffset: Int = 0,
    verticalOffset: Int = defaultVerticalOffset,
    onPreviewKeyEvent: (KeyEvent) -> Boolean = { false },
    onKeyEvent: (KeyEvent) -> Boolean = { false },
    menu: (TrayMenuBuilder.() -> Unit)? = null,
    content: @Composable DialogWindowScope.() -> Unit,
) {
    val iconContent: @Composable () -> Unit = {
        Image(painter = icon, contentDescription = null, modifier = Modifier.fillMaxSize())
    }
    TrayApp(
        iconContent = iconContent,
        iconRenderProperties = iconRenderProperties,
        tooltip = tooltip,
        state = state,
        windowSize = windowSize,
        visibleOnStart = visibleOnStart,
        enterTransition = enterTransition,
        exitTransition = exitTransition,
        transparent = transparent,
        windowsTitle = windowsTitle,
        windowIcon = windowIcon,
        undecorated = undecorated,
        resizable = resizable,
        horizontalOffset = horizontalOffset,
        verticalOffset = verticalOffset,
        onPreviewKeyEvent = onPreviewKeyEvent,
        onKeyEvent = onKeyEvent,
        menu = menu,
        content = content,
    )
}

/** Painter on Windows, ImageVector on macOS/Linux */
@ExperimentalTrayAppApi
@Composable
fun ApplicationScope.TrayApp(
    windowsIcon: Painter,
    macLinuxIcon: ImageVector,
    tint: Color? = null,
    iconRenderProperties: IconRenderProperties = IconRenderProperties.forCurrentOperatingSystem(),
    tooltip: String,
    state: TrayAppState? = null,
    windowSize: DpSize? = null,
    visibleOnStart: Boolean = false,
    enterTransition: EnterTransition = defaultTrayAppEnterTransition,
    exitTransition: ExitTransition = defaultTrayAppExitTransition,
    transparent: Boolean = true,
    windowsTitle: String = "",
    windowIcon: Painter? = null,
    undecorated: Boolean = true,
    resizable: Boolean = false,
    horizontalOffset: Int = 0,
    verticalOffset: Int = defaultVerticalOffset,
    onPreviewKeyEvent: (KeyEvent) -> Boolean = { false },
    onKeyEvent: (KeyEvent) -> Boolean = { false },
    menu: (TrayMenuBuilder.() -> Unit)? = null,
    content: @Composable DialogWindowScope.() -> Unit,
) {
    if (getOperatingSystem() == WINDOWS) {
        TrayApp(
            icon = windowsIcon,
            iconRenderProperties = iconRenderProperties,
            tooltip = tooltip,
            state = state,
            windowSize = windowSize,
            visibleOnStart = visibleOnStart,
            enterTransition = enterTransition,
            exitTransition = exitTransition,
            transparent = transparent,
            windowsTitle = windowsTitle,
            windowIcon = windowIcon,
            undecorated = undecorated,
            resizable = resizable,
            horizontalOffset = horizontalOffset,
            verticalOffset = verticalOffset,
            onPreviewKeyEvent = onPreviewKeyEvent,
            onKeyEvent = onKeyEvent,
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
            enterTransition = enterTransition,
            exitTransition = exitTransition,
            transparent = transparent,
            windowsTitle = windowsTitle,
            windowIcon = windowIcon,
            undecorated = undecorated,
            resizable = resizable,
            horizontalOffset = horizontalOffset,
            verticalOffset = verticalOffset,
            onPreviewKeyEvent = onPreviewKeyEvent,
            onKeyEvent = onKeyEvent,
            menu = menu,
            content = content,
        )
    }
}

@ExperimentalTrayAppApi
@Composable
fun ApplicationScope.TrayApp(
    icon: DrawableResource,
    iconRenderProperties: IconRenderProperties = IconRenderProperties.forCurrentOperatingSystem(),
    tooltip: String,
    state: TrayAppState? = null,
    windowSize: DpSize? = null,
    visibleOnStart: Boolean = false,
    enterTransition: EnterTransition = defaultTrayAppEnterTransition,
    exitTransition: ExitTransition = defaultTrayAppExitTransition,
    transparent: Boolean = true,
    windowsTitle: String = "",
    windowIcon: Painter? = null,
    undecorated: Boolean = true,
    resizable: Boolean = false,
    horizontalOffset: Int = 0,
    verticalOffset: Int = defaultVerticalOffset,
    onPreviewKeyEvent: (KeyEvent) -> Boolean = { false },
    onKeyEvent: (KeyEvent) -> Boolean = { false },
    menu: (TrayMenuBuilder.() -> Unit)? = null,
    content: @Composable DialogWindowScope.() -> Unit,
) {
    TrayApp(
        icon = painterResource(icon),
        iconRenderProperties = iconRenderProperties,
        tooltip = tooltip,
        state = state,
        windowSize = windowSize,
        visibleOnStart = visibleOnStart,
        enterTransition = enterTransition,
        exitTransition = exitTransition,
        transparent = transparent,
        windowsTitle = windowsTitle,
        windowIcon = windowIcon,
        undecorated = undecorated,
        resizable = resizable,
        horizontalOffset = horizontalOffset,
        verticalOffset = verticalOffset,
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
    windowSize: DpSize? = null,
    visibleOnStart: Boolean = false,
    enterTransition: EnterTransition = defaultTrayAppEnterTransition,
    exitTransition: ExitTransition = defaultTrayAppExitTransition,
    transparent: Boolean = true,
    windowsTitle: String = "",
    windowIcon: Painter? = null,
    undecorated: Boolean = true,
    resizable: Boolean = false,
    horizontalOffset: Int = 0,
    verticalOffset: Int = defaultVerticalOffset,
    onPreviewKeyEvent: (KeyEvent) -> Boolean = { false },
    onKeyEvent: (KeyEvent) -> Boolean = { false },
    menu: (TrayMenuBuilder.() -> Unit)? = null,
    content: @Composable DialogWindowScope.() -> Unit,
) {
    if (getOperatingSystem() == WINDOWS) {
        TrayApp(
            icon = painterResource(windowsIcon),
            iconRenderProperties = iconRenderProperties,
            tooltip = tooltip,
            state = state,
            windowSize = windowSize,
            visibleOnStart = visibleOnStart,
            enterTransition = enterTransition,
            exitTransition = exitTransition,
            transparent = transparent,
            windowsTitle = windowsTitle,
            windowIcon = windowIcon,
            undecorated = undecorated,
            resizable = resizable,
            horizontalOffset = horizontalOffset,
            verticalOffset = verticalOffset,
            onPreviewKeyEvent = onPreviewKeyEvent,
            onKeyEvent = onKeyEvent,
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
            enterTransition = enterTransition,
            exitTransition = exitTransition,
            transparent = transparent,
            windowsTitle = windowsTitle,
            windowIcon = windowIcon,
            undecorated = undecorated,
            resizable = resizable,
            horizontalOffset = horizontalOffset,
            verticalOffset = verticalOffset,
            onPreviewKeyEvent = onPreviewKeyEvent,
            onKeyEvent = onKeyEvent,
            menu = menu,
            content = content,
        )
    }
}

// --------------------- Core router ---------------------

@ExperimentalTrayAppApi
@Composable
fun ApplicationScope.TrayApp(
    iconContent: @Composable () -> Unit,
    iconRenderProperties: IconRenderProperties = IconRenderProperties.forCurrentOperatingSystem(),
    tooltip: String,
    state: TrayAppState? = null,
    windowSize: DpSize? = null,
    visibleOnStart: Boolean = false,
    enterTransition: EnterTransition = defaultTrayAppEnterTransition,
    exitTransition: ExitTransition = defaultTrayAppExitTransition,
    transparent: Boolean = true,
    windowsTitle: String = "",
    windowIcon: Painter? = null,
    undecorated: Boolean = true,
    resizable: Boolean = false,
    horizontalOffset: Int = 0,
    verticalOffset: Int = defaultVerticalOffset,
    onPreviewKeyEvent: (KeyEvent) -> Boolean = { false },
    onKeyEvent: (KeyEvent) -> Boolean = { false },
    menu: (TrayMenuBuilder.() -> Unit)? = null,
    content: @Composable DialogWindowScope.() -> Unit,
) {
    when (getOperatingSystem()) {
        OperatingSystem.LINUX -> TrayAppImplLinux(
            iconContent,
            iconRenderProperties,
            tooltip,
            state,
            windowSize,
            visibleOnStart,
            enterTransition,
            exitTransition,
            transparent,
            windowsTitle,
            windowIcon,
            undecorated,
            resizable,
            horizontalOffset,
            verticalOffset,
            onPreviewKeyEvent,
            onKeyEvent,
            menu,
            content
        )

        else -> TrayAppImplOriginal(
            iconContent,
            iconRenderProperties,
            tooltip,
            state,
            windowSize,
            visibleOnStart,
            enterTransition,
            exitTransition,
            transparent,
            windowsTitle,
            windowIcon,
            undecorated,
            resizable,
            horizontalOffset,
            verticalOffset,
            onPreviewKeyEvent,
            onKeyEvent,
            menu,
            content
        )
    }
}

// --------------------- Impl: macOS/Windows ---------------------

@Composable
private fun ApplicationScope.TrayAppImplOriginal(
    iconContent: @Composable () -> Unit,
    iconRenderProperties: IconRenderProperties,
    tooltip: String,
    state: TrayAppState?,
    windowSize: DpSize?,
    visibleOnStart: Boolean,
    enterTransition: EnterTransition,
    exitTransition: ExitTransition,
    transparent: Boolean,
    windowsTitle: String,
    windowIcon: Painter?,
    undecorated: Boolean,
    resizable: Boolean,
    horizontalOffset: Int,
    verticalOffset: Int,
    onPreviewKeyEvent: (KeyEvent) -> Boolean,
    onKeyEvent: (KeyEvent) -> Boolean,
    menu: (TrayMenuBuilder.() -> Unit)?,
    content: @Composable DialogWindowScope.() -> Unit,
) {
    val trayAppState = state ?: rememberTrayAppState(
        initialWindowSize = windowSize ?: DpSize(300.dp, 200.dp),
        initiallyVisible = visibleOnStart,
        initialDismissMode = TrayWindowDismissMode.AUTO
    )
    val isVisible by trayAppState.isVisible.collectAsState()
    val currentWindowSize by trayAppState.windowSize.collectAsState()
    val dismissMode by trayAppState.dismissMode.collectAsState()

    val tray = remember { NativeTray() }
    val isDark = isMenuBarInDarkMode()
    val contentHash = ComposableIconUtils.calculateContentHash(iconRenderProperties, iconContent) + isDark.hashCode()
    val pngIconPath = remember(contentHash) {
        ComposableIconUtils.renderComposableToPngFile(iconRenderProperties, iconContent)
    }
    val windowsIconPath = remember(contentHash) {
        if (getOperatingSystem() == WINDOWS) ComposableIconUtils.renderComposableToIcoFile(
            iconRenderProperties,
            iconContent
        )
        else pngIconPath
    }
    val menuHash = MenuContentHash.calculateMenuHash(menu)

    var shouldShowWindow by remember { mutableStateOf(false) }

    var lastPrimaryActionAt by remember { mutableStateOf(0L) }
    val toggleDebounceMs = 280L

    var lastShownAt by remember { mutableStateOf(0L) }
    var lastHiddenAt by remember { mutableStateOf(0L) }
    val minVisibleDurationMs = 350L
    val minHiddenDurationMs = 250L

    var lastFocusLostAt by remember { mutableStateOf(0L) }
    var autoHideEnabledAt by remember { mutableStateOf(0L) }

    // Position pre-computed at click time so the LaunchedEffect can use it immediately.
    var pendingPosition by remember { mutableStateOf<WindowPosition?>(null) }

    // Store window reference for macOS Space detection
    var windowRef by remember { mutableStateOf<java.awt.Window?>(null) }

    val dialogState = rememberDialogState(size = currentWindowSize)
    LaunchedEffect(currentWindowSize) { dialogState.size = currentWindowSize }

    // Visibility controller for exit-finish observation; content will NOT be disposed.
    val visibleState = remember { MutableTransitionState(false) }

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

    val internalPrimaryAction: () -> Unit = {
        val now = System.currentTimeMillis()
        if (now - lastPrimaryActionAt >= toggleDebounceMs) {
            lastPrimaryActionAt = now
            if (isVisible) {
                // On macOS, check if window has focus before hiding
                // If it doesn't have focus (e.g., on another Space), bring it to front instead
                if (getOperatingSystem() == MACOS && windowRef != null) {
                    val hasFocus = runCatching { windowRef!!.isFocused() }.getOrElse { false }
                    if (!hasFocus) {
                        // Window is not focused (likely on another Space), bring it to current Space
                        invokeLater {
                            runCatching { MacTrayLoader.lib.tray_set_windows_move_to_active_space() }
                            runCatching { MacOSWindowManager().setMoveToActiveSpace(windowRef!!) }
                            runCatching {
                                windowRef!!.toFront()
                                windowRef!!.requestFocus()
                                windowRef!!.requestFocusInWindow()
                            }
                        }
                    } else {
                        requestHideExplicit()
                    }
                } else {
                    requestHideExplicit()
                }
            } else {
                if (now - lastHiddenAt >= minHiddenDurationMs) {
                    if (getOperatingSystem() == WINDOWS && (now - lastFocusLostAt) < 300) {
                        // ignore immediate re-show after focus loss on Windows
                    } else {
                        // Pre-compute position at click time: the native status item
                        // geometry is guaranteed to be available right now.
                        runCatching {
                            val widthPx = currentWindowSize.width.value.toInt()
                            val heightPx = currentWindowSize.height.value.toInt()
                            pendingPosition = getTrayWindowPositionForInstance(
                                tray.instanceKey(), widthPx, heightPx, horizontalOffset, verticalOffset
                            )
                        }
                        trayAppState.show()
                    }
                }
            }
        }
    }

    LaunchedEffect(isVisible) {
        // Drive transition state
        visibleState.targetState = isVisible

        if (isVisible) {
            if (!shouldShowWindow) {
                val preComputed = pendingPosition
                pendingPosition = null

                val position = if (preComputed != null && preComputed !is WindowPosition.PlatformDefault) {
                    debugln { "[TrayApp] Using preComputed position: $preComputed" }
                    preComputed
                } else {
                    // Fallback: poll for position (e.g. initiallyVisible or programmatic show)
                    // Wait for Windows to finish reorganizing tray icons after adding a new one.
                    // Windows moves icons around after creation, so we need to wait and re-poll.
                    debugln { "[TrayApp] No preComputed position, waiting for tray to stabilize..." }
                    delay(400) // Give Windows time to reorganize tray icons

                    val widthPx = currentWindowSize.width.value.toInt()
                    val heightPx = currentWindowSize.height.value.toInt()

                    // On Windows, force a fresh position capture via the native API
                    if (getOperatingSystem() == WINDOWS) {
                        debugln { "[TrayApp] Re-capturing tray position from native API..." }
                        WindowsTrayInitializer.refreshPosition(tray.instanceKey())
                        delay(50) // Let the position update propagate
                    }

                    var pos: WindowPosition = WindowPosition.PlatformDefault
                    val deadline = System.currentTimeMillis() + 3000
                    while (pos is WindowPosition.PlatformDefault && System.currentTimeMillis() < deadline) {
                        pos = getTrayWindowPositionForInstance(
                            tray.instanceKey(), widthPx, heightPx, horizontalOffset, verticalOffset
                        )
                        debugln { "[TrayApp] Polled position: $pos" }
                        if (pos is WindowPosition.PlatformDefault) delay(250)
                    }
                    pos
                }
                debugln { "[TrayApp] Setting dialogState.position = $position" }
                dialogState.position = position

                // Wait for Compose to apply the position before showing the window
                // This prevents the window from flashing at the wrong position
                delay(150) // Give Compose time to recompose with new position

                if (getOperatingSystem() == WINDOWS) {
                    autoHideEnabledAt = System.currentTimeMillis() + 1000
                }
                debugln { "[TrayApp] Now showing window" }
                shouldShowWindow = true
                lastShownAt = System.currentTimeMillis()
            }
        } else {
            // Wait for exit animation to finish, then actually hide the window
            if (shouldShowWindow) {
                snapshotFlow { visibleState.isIdle && !visibleState.currentState }.first { it }
                shouldShowWindow = false
                lastHiddenAt = System.currentTimeMillis()
            }
        }
    }

    LaunchedEffect(pngIconPath, windowsIconPath, tooltip, internalPrimaryAction, menu, contentHash, menuHash) {
        tray.update(pngIconPath, windowsIconPath, tooltip, internalPrimaryAction, menu)
    }

    LaunchedEffect(Unit) {
        if (getOperatingSystem() == MACOS) {
            WindowVisibilityMonitor.hasAnyVisibleWindows.collectLatest { hasVisible ->
                runCatching {
                    val manager = MacOSWindowManager()
                    if (hasVisible) manager.showInDock() else manager.hideFromDock()
                }
            }
        }
    }

    DisposableEffect(Unit) { onDispose { tray.dispose() } }

    DialogWindow(
        onCloseRequest = { requestHideExplicit() },
        title = windowsTitle,
        icon = windowIcon,
        undecorated = undecorated,
        resizable = resizable,
        focusable = true,
        alwaysOnTop = true,
        transparent = transparent,
        visible = shouldShowWindow,
        state = dialogState,
        onPreviewKeyEvent = onPreviewKeyEvent,
        onKeyEvent = onKeyEvent,
    ) {
        DisposableEffect(shouldShowWindow, dismissMode) {
            if (!shouldShowWindow) return@DisposableEffect onDispose { }

            // Store window reference for Space detection on macOS
            windowRef = window

            try { window.name = WindowVisibilityMonitor.TRAY_DIALOG_NAME } catch (_: Throwable) {}
            runCatching { WindowVisibilityMonitor.recompute() }

            debugln { "[TrayApp] Window shown at native position: x=${window.x}, y=${window.y}, dialogState.position=${dialogState.position}" }

            invokeLater {
                // Move the popup to the current Space before bringing it to front (macOS)
                if (getOperatingSystem() == MACOS) {
                    runCatching { MacTrayLoader.lib.tray_set_windows_move_to_active_space() }
                    runCatching { MacOSWindowManager().setMoveToActiveSpace(window) }
                }
                debugln { "[TrayApp] After invokeLater: window at x=${window.x}, y=${window.y}" }
                runCatching {
                    window.toFront()
                    window.requestFocus()
                    window.requestFocusInWindow()
                }
            }

            val focusListener = object : WindowFocusListener {
                override fun windowGainedFocus(e: WindowEvent?) = Unit
                override fun windowLostFocus(e: WindowEvent?) {
                    lastFocusLostAt = System.currentTimeMillis()
                    if (getOperatingSystem() == WINDOWS && lastFocusLostAt < autoHideEnabledAt) return
                    if (dismissMode == TrayWindowDismissMode.AUTO) requestHideExplicit()
                }
            }

            val macWatcher = if (dismissMode == TrayWindowDismissMode.AUTO && getOperatingSystem() == MACOS) {
                MacOutsideClickWatcher(
                    windowSupplier = { window },
                    onOutsideClick = { invokeLater { requestHideExplicit() } }
                ).also { it.start() }
            } else null

            val linuxWatcher =
                if (dismissMode == TrayWindowDismissMode.AUTO && getOperatingSystem() == OperatingSystem.LINUX) {
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
                macWatcher?.stop(); linuxWatcher?.stop(); windowsWatcher?.stop()
                runCatching { WindowVisibilityMonitor.recompute() }
                windowRef = null
            }
        }

        // ---- Persistent (non-disposing) visibility wrapper ----
        PersistentAnimatedVisibility(
            visibleState = visibleState,
            enter = enterTransition,
            exit = exitTransition
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer()
                    // Child can still opt into its own per-node enter/exit if desired:
                    .animateEnterExit()
            ) { content() }
        }
    }
}

// --------------------- Impl: Linux ---------------------

@Composable
private fun ApplicationScope.TrayAppImplLinux(
    iconContent: @Composable () -> Unit,
    iconRenderProperties: IconRenderProperties,
    tooltip: String,
    state: TrayAppState?,
    windowSize: DpSize?,
    visibleOnStart: Boolean,
    enterTransition: EnterTransition,
    exitTransition: ExitTransition,
    transparent: Boolean,
    windowsTitle: String,
    windowIcon: Painter?,
    undecorated: Boolean,
    resizable: Boolean,
    horizontalOffset: Int,
    verticalOffset: Int,
    onPreviewKeyEvent: (KeyEvent) -> Boolean,
    onKeyEvent: (KeyEvent) -> Boolean,
    menu: (TrayMenuBuilder.() -> Unit)?,
    content: @Composable DialogWindowScope.() -> Unit,
) {
    val trayAppState = state ?: rememberTrayAppState(
        initialWindowSize = windowSize ?: DpSize(300.dp, 200.dp),
        initiallyVisible = visibleOnStart,
        initialDismissMode = TrayWindowDismissMode.AUTO
    )
    val isVisible by trayAppState.isVisible.collectAsState()
    val currentWindowSize by trayAppState.windowSize.collectAsState()
    val dismissMode by trayAppState.dismissMode.collectAsState()

    val tray = remember { NativeTray() }
    val instanceKey = remember { tray.instanceKey() }

    val isDark = isMenuBarInDarkMode()
    val contentHash = ComposableIconUtils.calculateContentHash(iconRenderProperties, iconContent) + isDark.hashCode()
    val pngIconPath =
        remember(contentHash) { ComposableIconUtils.renderComposableToPngFile(iconRenderProperties, iconContent) }
    val windowsIconPath = pngIconPath
    val menuHash = MenuContentHash.calculateMenuHash(menu)

    var shouldShowWindow by remember { mutableStateOf(false) }
    var lastPrimaryActionAt by remember { mutableStateOf(0L) }
    val toggleDebounceMs = 280L
    var lastShownAt by remember { mutableStateOf(0L) }
    var lastHiddenAt by remember { mutableStateOf(0L) }
    val minVisibleDurationMs = 350L
    val minHiddenDurationMs = 250L

    val initialPositionForFirstFrame = remember(instanceKey, currentWindowSize, horizontalOffset, verticalOffset) {
        val w = currentWindowSize.width.value.toInt()
        val h = currentWindowSize.height.value.toInt()
        getTrayWindowPositionForInstance(instanceKey, w, h, horizontalOffset, verticalOffset)
    }

    val dialogState = rememberDialogState(position = initialPositionForFirstFrame, size = currentWindowSize)
    LaunchedEffect(currentWindowSize) { dialogState.size = currentWindowSize }

    // Visibility controller for exit-finish detection; content will NOT be disposed.
    val visibleState = remember { MutableTransitionState(false) }

    val requestHideExplicit: () -> Unit = {
        val now = System.currentTimeMillis()
        val sinceShow = now - lastShownAt
        if (sinceShow >= minVisibleDurationMs) {
            trayAppState.hide(); lastHiddenAt = now
        } else {
            val wait = (minVisibleDurationMs - sinceShow).coerceAtLeast(0)
            CoroutineScope(Dispatchers.IO).launch {
                delay(wait)
                trayAppState.hide(); lastHiddenAt = System.currentTimeMillis()
            }
        }
    }

    val internalPrimaryAction: () -> Unit = action@{
        val now = System.currentTimeMillis()
        if (now - lastPrimaryActionAt < toggleDebounceMs) return@action
        lastPrimaryActionAt = now
        if (isVisible) {
            requestHideExplicit()
        } else if (now - lastHiddenAt >= minHiddenDurationMs) {
            val w = currentWindowSize.width.value.toInt()
            val h = currentWindowSize.height.value.toInt()
            dialogState.position = getTrayWindowPositionForInstance(instanceKey, w, h, horizontalOffset, verticalOffset)
            trayAppState.show()
        }
    }

    LaunchedEffect(isVisible) {
        // Drive transition state
        visibleState.targetState = isVisible

        if (isVisible) {
            if (!shouldShowWindow) {
                shouldShowWindow = true
                lastShownAt = System.currentTimeMillis()
            }
        } else {
            if (shouldShowWindow) {
                snapshotFlow { visibleState.isIdle && !visibleState.currentState }.first { it }
                shouldShowWindow = false
                lastHiddenAt = System.currentTimeMillis()
            }
        }
    }

    // Re-anchor when visible and size/offset changes
    LaunchedEffect(currentWindowSize, horizontalOffset, verticalOffset, shouldShowWindow) {
        if (shouldShowWindow) {
            val w = currentWindowSize.width.value.toInt()
            val h = currentWindowSize.height.value.toInt()
            dialogState.position = getTrayWindowPositionForInstance(instanceKey, w, h, horizontalOffset, verticalOffset)
        }
    }

    LaunchedEffect(pngIconPath, windowsIconPath, tooltip, internalPrimaryAction, menu, contentHash, menuHash) {
        tray.update(pngIconPath, windowsIconPath, tooltip, internalPrimaryAction, menu)
    }

    DisposableEffect(Unit) { onDispose { tray.dispose() } }

    DialogWindow(
        onCloseRequest = { requestHideExplicit() },
        title = windowsTitle,
        icon = windowIcon,
        undecorated = undecorated,
        resizable = resizable,
        focusable = shouldShowWindow,
        alwaysOnTop = true,
        transparent = transparent,
        visible = shouldShowWindow,
        state = dialogState,
        onPreviewKeyEvent = onPreviewKeyEvent,
        onKeyEvent = onKeyEvent,
    ) {
        DisposableEffect(shouldShowWindow, dismissMode) {
            if (!shouldShowWindow) return@DisposableEffect onDispose { }

            runCatching {
                val w = currentWindowSize.width.value.toInt()
                val h = currentWindowSize.height.value.toInt()
                dialogState.position =
                    getTrayWindowPositionForInstance(instanceKey, w, h, horizontalOffset, verticalOffset)
            }

            val linuxWatcher = if (dismissMode == TrayWindowDismissMode.AUTO) {
                LinuxOutsideClickWatcher(
                    windowSupplier = { window },
                    onOutsideClick = { invokeLater { requestHideExplicit() } }
                ).also { it.start() }
            } else null

            window.addWindowFocusListener(object : WindowFocusListener {
                override fun windowGainedFocus(e: WindowEvent?) = Unit
                override fun windowLostFocus(e: WindowEvent?) {
                    if (dismissMode == TrayWindowDismissMode.AUTO) requestHideExplicit()
                }
            })

            onDispose { linuxWatcher?.stop() }
        }

        // ---- Persistent (non-disposing) visibility wrapper ----
        PersistentAnimatedVisibility(
            visibleState = visibleState,
            enter = enterTransition,
            exit = exitTransition
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer()
                    .animateEnterExit()
            ) { content() }
        }
    }
}
