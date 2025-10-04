@file:OptIn(ExperimentalTrayAppApi::class)

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
import androidx.compose.ui.input.key.KeyEvent
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
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import java.awt.EventQueue.invokeLater
import java.awt.event.WindowEvent
import java.awt.event.WindowFocusListener


/**
 * Creates a tray-based desktop application with support for customizable icons, tooltips, menus,
 * and composable content. The application integrates with the system's tray or menu bar and
 * supports optional window functionality.
 *
 * @param icon The primary icon to display in the system tray or menu bar.
 * @param tint Optional color tint for the icon. Defaults to null, using the appropriate dark or light mode color.
 * @param iconRenderProperties Properties for rendering the icon, with defaults based on the operating system.
 * @param tooltip Text to be displayed as a tooltip when hovering over the tray icon.
 * @param state Optional state for managing the tray application's properties or lifecycle.
 * @param windowSize Optional initial size of the window, if a window is displayed.
 * @param visibleOnStart Whether the application's window should be visible on startup. Defaults to false.
 * @param fadeDurationMs Duration in milliseconds for fade animations. Defaults to 200ms.
 * @param animationSpec Animation specification for fading effects. Defaults to a smooth easing with the specified duration.
 * @param transparent Whether the application's window should be transparent. Defaults to true.
 * @param windowsTitle Title of the window for Windows operating systems. Defaults to an empty string.
 * @param windowIcon Optional icon to be used for the application's window.
 * @param onPreviewKeyEvent Handler for previewing key events before they are processed. Returns `true` if the event is consumed.
 * @param onKeyEvent Handler for processing key events. Returns `true` if the event is consumed.
 * @param menu Optional builder block for defining the application's tray menu. Defaults to null if no menu is needed.
 * @param content Composable content to be displayed within the application's window.
 */
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
    fadeDurationMs: Int = 200,
    animationSpec: AnimationSpec<Float> = tween(durationMillis = fadeDurationMs, easing = EaseInOut),
    transparent: Boolean = true,
    windowsTitle: String = "",
    windowIcon: Painter? = null,
    onPreviewKeyEvent: (KeyEvent) -> Boolean = { false },
    onKeyEvent: (KeyEvent) -> Boolean = { false },
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
        fadeDurationMs = fadeDurationMs,
        animationSpec = animationSpec,
        transparent = transparent,
        windowsTitle = windowsTitle,
        windowIcon = windowIcon,
        onPreviewKeyEvent = onPreviewKeyEvent,
        onKeyEvent = onKeyEvent,
        menu = menu,
        content = content,
    )
}

/**
 * Creates a system tray application with the provided configuration.
 *
 * @param icon The icon displayed in the system tray for this application.
 * @param iconRenderProperties The properties defining how the icon is rendered. Default is based on the current operating system.
 * @param tooltip The tooltip text displayed when hovering over the tray icon.
 * @param state The state of the tray app, which can be used to manage its visibility and behavior. Defaults to `null`.
 * @param windowSize The size of the application window when displayed. Defaults to `null`.
 * @param visibleOnStart Determines whether the application window is visible when the app starts. Defaults to `false`.
 * @param fadeDurationMs The duration of the fade animation (in milliseconds) when showing or hiding the window. Defaults to `200`.
 * @param animationSpec The animation specification used for window fade transitions. Defaults to an easing `tween` animation.
 * @param transparent Indicates if the application window background should be transparent. Defaults to `true`.
 * @param windowsTitle The title of the window displayed in the task manager or window list on Windows systems. Defaults to an empty string.
 * @param windowIcon The icon displayed for the application window (if any). Can be `null`.
 * @param onPreviewKeyEvent A callback invoked before key events are dispatched. It can intercept and handle key events. Defaults to returning `false`.
 * @param onKeyEvent A callback invoked to handle key events. Defaults to returning `false`.
 * @param menu An optional builder block to define the tray menu items.
 * @param content The content displayed in the tray application's main window.
 */
@ExperimentalTrayAppApi
@Composable
fun ApplicationScope.TrayApp(
    icon: Painter,
    iconRenderProperties: IconRenderProperties = IconRenderProperties.forCurrentOperatingSystem(),
    tooltip: String,
    state: TrayAppState? = null,
    windowSize: DpSize? = null,
    visibleOnStart: Boolean = false,
    fadeDurationMs: Int = 200,
    animationSpec: AnimationSpec<Float> = tween(durationMillis = fadeDurationMs, easing = EaseInOut),
    transparent: Boolean = true,
    windowsTitle: String = "",
    windowIcon: Painter? = null,
    onPreviewKeyEvent: (KeyEvent) -> Boolean = { false },
    onKeyEvent: (KeyEvent) -> Boolean = { false },
    menu: (TrayMenuBuilder.() -> Unit)? = null,
    content: @Composable () -> Unit,
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
        fadeDurationMs = fadeDurationMs,
        animationSpec = animationSpec,
        transparent = transparent,
        windowsTitle = windowsTitle,
        windowIcon = windowIcon,
        onPreviewKeyEvent = onPreviewKeyEvent,
        onKeyEvent = onKeyEvent,
        menu = menu,
        content = content,
    )
}

/**
 * Composable function for displaying a system tray application with a GUI window.
 * This function differs behavior based on the user's operating system.
 *
 * @param windowsIcon Icon displayed in the system tray on Windows systems.
 * @param macLinuxIcon Icon displayed in the system tray on macOS and Linux systems.
 * @param tint Optional tint applied to the macOS and Linux tray icon.
 * @param iconRenderProperties Properties determining how the icon should be rendered.
 * @param tooltip Text displayed as a tooltip when hovering over the tray icon.
 * @param state Optional state for managing the tray application window (visibility, etc.).
 * @param windowSize Desired size of the window when opened, if applicable.
 * @param visibleOnStart Whether the window should be visible immediately after starting the app.
 * @param fadeDurationMs Duration of the fade-in and fade-out animations for showing/hiding the window, in milliseconds.
 * @param animationSpec Animation specification for fade effects.
 * @param transparent Whether the window's background should be transparent.
 * @param windowsTitle Title of the GUI window on Windows.
 * @param windowIcon Icon displayed in the top-left corner of the window on Windows.
 * @param onPreviewKeyEvent Lambda for handling preview key events invoked before onKeyEvent. Defaults to ignoring key events.
 * @param onKeyEvent Lambda for handling key events when the window has focus. Defaults to ignoring key events.
 * @param menu Optional lambda for building the context menu attached to the tray icon.
 * @param content Composable content displayed within the GUI window.
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
    windowSize: DpSize? = null,
    visibleOnStart: Boolean = false,
    fadeDurationMs: Int = 200,
    animationSpec: AnimationSpec<Float> = tween(durationMillis = fadeDurationMs, easing = EaseInOut),
    transparent: Boolean = true,
    windowsTitle: String = "",
    windowIcon: Painter? = null,
    onPreviewKeyEvent: (KeyEvent) -> Boolean = { false },
    onKeyEvent: (KeyEvent) -> Boolean = { false },
    menu: (TrayMenuBuilder.() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    if (getOperatingSystem() == WINDOWS) {
        TrayApp(
            icon = windowsIcon,
            iconRenderProperties = iconRenderProperties,
            tooltip = tooltip,
            state = state,
            windowSize = windowSize,
            visibleOnStart = visibleOnStart,
            fadeDurationMs = fadeDurationMs,
            animationSpec = animationSpec,
            transparent = transparent,
            windowsTitle = windowsTitle,
            windowIcon = windowIcon,
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
            onPreviewKeyEvent = onPreviewKeyEvent,
            onKeyEvent = onKeyEvent,
            tooltip = tooltip,
            state = state,
            windowSize = windowSize,
            visibleOnStart = visibleOnStart,
            fadeDurationMs = fadeDurationMs,
            animationSpec = animationSpec,
            transparent = transparent,
            windowsTitle = windowsTitle,
            windowIcon = windowIcon,
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
    fadeDurationMs: Int = 200,
    animationSpec: AnimationSpec<Float> = tween(durationMillis = fadeDurationMs, easing = EaseInOut),
    transparent: Boolean = true,
    windowsTitle: String = "",
    windowIcon: Painter? = null,
    onPreviewKeyEvent: (KeyEvent) -> Boolean = { false },
    onKeyEvent: (KeyEvent) -> Boolean = { false },
    menu: (TrayMenuBuilder.() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    TrayApp(
        icon = painterResource(icon),
        iconRenderProperties = iconRenderProperties,
        tooltip = tooltip,
        state = state,
        windowSize = windowSize,
        visibleOnStart = visibleOnStart,
        fadeDurationMs = fadeDurationMs,
        animationSpec = animationSpec,
        transparent = transparent,
        windowsTitle = windowsTitle,
        windowIcon = windowIcon,
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
    fadeDurationMs: Int = 200,
    animationSpec: AnimationSpec<Float> = tween(durationMillis = fadeDurationMs, easing = EaseInOut),
    transparent: Boolean = true,
    windowsTitle: String = "",
    windowIcon: Painter? = null,
    onPreviewKeyEvent: (KeyEvent) -> Boolean = { false },
    onKeyEvent: (KeyEvent) -> Boolean = { false },
    menu: (TrayMenuBuilder.() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    if (getOperatingSystem() == WINDOWS) {
        TrayApp(
        icon = painterResource(windowsIcon),
        iconRenderProperties = iconRenderProperties,
        tooltip = tooltip,
        state = state,
        windowSize = windowSize,
        visibleOnStart = visibleOnStart,
        fadeDurationMs = fadeDurationMs,
        animationSpec = animationSpec,
        transparent = transparent,
        windowsTitle = windowsTitle,
        windowIcon = windowIcon,
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
            onPreviewKeyEvent = onPreviewKeyEvent,
            onKeyEvent = onKeyEvent,
            tooltip = tooltip,
            state = state,
            windowSize = windowSize,
            visibleOnStart = visibleOnStart,
            fadeDurationMs = fadeDurationMs,
            animationSpec = animationSpec,
            transparent = transparent,
            windowsTitle = windowsTitle,
            windowIcon = windowIcon,
            menu = menu,
            content = content,
        )
    }
}

// --------------------- Core (routes per platform) ---------------------

@ExperimentalTrayAppApi
@Composable
fun ApplicationScope.TrayApp(
    iconContent: @Composable () -> Unit,
    iconRenderProperties: IconRenderProperties = IconRenderProperties.forCurrentOperatingSystem(),
    tooltip: String,
    state: TrayAppState? = null,
    windowSize: DpSize? = null,
    visibleOnStart: Boolean = false,
    fadeDurationMs: Int = 200,
    animationSpec: AnimationSpec<Float> = tween(durationMillis = fadeDurationMs, easing = EaseInOut),
    transparent: Boolean = true,
    windowsTitle: String = "",
    windowIcon: Painter? = null,
    onPreviewKeyEvent: (KeyEvent) -> Boolean = { false },
    onKeyEvent: (KeyEvent) -> Boolean = { false },
    menu: (TrayMenuBuilder.() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    when (getOperatingSystem()) {
        OperatingSystem.LINUX -> TrayAppImplLinux(
            iconContent, iconRenderProperties, tooltip, state, windowSize,
            visibleOnStart, fadeDurationMs, animationSpec, transparent, windowsTitle, windowIcon, onPreviewKeyEvent, onKeyEvent, menu, content
        )
        else -> TrayAppImplOriginal(
            iconContent, iconRenderProperties, tooltip, state, windowSize,
            visibleOnStart, fadeDurationMs, animationSpec, transparent, windowsTitle, windowIcon, onPreviewKeyEvent, onKeyEvent, menu, content
        )
    }
}

// --------------------- Impl: Original (macOS/Windows) ---------------------

@Composable
private fun ApplicationScope.TrayAppImplOriginal(
    iconContent: @Composable () -> Unit,
    iconRenderProperties: IconRenderProperties,
    tooltip: String,
    state: TrayAppState?,
    windowSize: DpSize?,
    visibleOnStart: Boolean,
    fadeDurationMs: Int,
    animationSpec: AnimationSpec<Float>,
    transparent: Boolean,
    windowsTitle: String,
    windowIcon: Painter?,
    onPreviewKeyEvent: (KeyEvent) -> Boolean,
    onKeyEvent: (KeyEvent) -> Boolean,
    menu: (TrayMenuBuilder.() -> Unit)?,
    content: @Composable () -> Unit,
) {
    // State holder
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
        if (getOperatingSystem() == WINDOWS)
            ComposableIconUtils.renderComposableToIcoFile(iconRenderProperties, iconContent)
        else pngIconPath
    }
    val menuHash = MenuContentHash.calculateMenuHash(menu)

    val alpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = animationSpec,
        label = "window_fade"
    )

    var shouldShowWindow by remember { mutableStateOf(false) }

    var lastPrimaryActionAt by remember { mutableStateOf(0L) }
    val toggleDebounceMs = 280L

    var lastShownAt by remember { mutableStateOf(0L) }
    var lastHiddenAt by remember { mutableStateOf(0L) }
    val minVisibleDurationMs = 350L
    val minHiddenDurationMs = 250L

    var lastFocusLostAt by remember { mutableStateOf(0L) }
    var autoHideEnabledAt by remember { mutableStateOf(0L) }

    val dialogState = rememberDialogState(size = currentWindowSize)

    LaunchedEffect(currentWindowSize) { dialogState.size = currentWindowSize }

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
                requestHideExplicit()
            } else {
                if (now - lastHiddenAt >= minHiddenDurationMs) {
                    if (getOperatingSystem() == WINDOWS && (now - lastFocusLostAt) < 300) {
                        // ignore immediate re-show after focus loss on Windows
                    } else {
                        trayAppState.show()
                    }
                }
            }
        }
    }

    LaunchedEffect(isVisible) {
        if (isVisible) {
            if (!shouldShowWindow) {
                // Slight delay lets the tray click/dock animations settle (macOS)
                delay(150)
                val widthPx = currentWindowSize.width.value.toInt()
                val heightPx = currentWindowSize.height.value.toInt()
                var position: WindowPosition = WindowPosition.PlatformDefault
                val deadline = System.currentTimeMillis() + 3000
                while (position is WindowPosition.PlatformDefault && System.currentTimeMillis() < deadline) {
                    position = getTrayWindowPositionForInstance(
                        tray.instanceKey(), widthPx, heightPx
                    )
                    delay(150)
                }
                dialogState.position = position

                if (getOperatingSystem() == WINDOWS) {
                    autoHideEnabledAt = System.currentTimeMillis() + 1000
                }
                shouldShowWindow = true
                lastShownAt = System.currentTimeMillis()
            }
        } else {
            delay(fadeDurationMs.toLong())
            shouldShowWindow = false
            lastHiddenAt = System.currentTimeMillis()
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
        undecorated = true,
        resizable = false,
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

            try { window.name = WindowVisibilityMonitor.TRAY_DIALOG_NAME } catch (_: Throwable) {}
            runCatching { WindowVisibilityMonitor.recompute() }

            invokeLater {
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
                macWatcher?.stop(); linuxWatcher?.stop(); windowsWatcher?.stop()
                runCatching { WindowVisibilityMonitor.recompute() }
            }
        }

        Box(
            modifier = Modifier.fillMaxSize().alpha(alpha)
        ) { content() }
    }
}

// --------------------- Impl: Linux (new logic) ---------------------

@Composable
private fun ApplicationScope.TrayAppImplLinux(
    iconContent: @Composable () -> Unit,
    iconRenderProperties: IconRenderProperties,
    tooltip: String,
    state: TrayAppState?,
    windowSize: DpSize?,
    visibleOnStart: Boolean,
    fadeDurationMs: Int,
    animationSpec: AnimationSpec<Float>,
    transparent: Boolean,
    windowsTitle: String,
    windowIcon: Painter?,
    onPreviewKeyEvent: (KeyEvent) -> Boolean,
    onKeyEvent: (KeyEvent) -> Boolean,
    menu: (TrayMenuBuilder.() -> Unit)?,
    content: @Composable () -> Unit,
) {
    // State holder (window always mounted; visibility toggled)
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
    val pngIconPath = remember(contentHash) { ComposableIconUtils.renderComposableToPngFile(iconRenderProperties, iconContent) }
    val windowsIconPath = pngIconPath // Linux doesn't use .ico
    val menuHash = MenuContentHash.calculateMenuHash(menu)

    val alpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = animationSpec,
        label = "window_fade"
    )

    var shouldShowWindow by remember { mutableStateOf(false) }
    var lastPrimaryActionAt by remember { mutableStateOf(0L) }
    val toggleDebounceMs = 280L
    var lastShownAt by remember { mutableStateOf(0L) }
    var lastHiddenAt by remember { mutableStateOf(0L) }
    val minVisibleDurationMs = 350L
    val minHiddenDurationMs = 250L

    // Seed initial position BEFORE creating DialogWindow
    val initialPositionForFirstFrame = remember(instanceKey, currentWindowSize) {
        val w = currentWindowSize.width.value.toInt()
        val h = currentWindowSize.height.value.toInt()
        getTrayWindowPositionForInstance(instanceKey, w, h)
    }

    val dialogState = rememberDialogState(position = initialPositionForFirstFrame, size = currentWindowSize)
    SideEffect { dialogState.position = initialPositionForFirstFrame }
    LaunchedEffect(currentWindowSize) { dialogState.size = currentWindowSize }

    val requestHideExplicit: () -> Unit = {
        val now = System.currentTimeMillis()
        val sinceShow = now - lastShownAt
        if (sinceShow >= minVisibleDurationMs) {
            trayAppState.hide(); lastHiddenAt = now
        } else {
            val wait = (minVisibleDurationMs - sinceShow).coerceAtLeast(0)
            CoroutineScope(Dispatchers.IO).launch {
                delay(wait.toLong())
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
            dialogState.position = getTrayWindowPositionForInstance(instanceKey, w, h)
            trayAppState.show()
        }
    }

    LaunchedEffect(isVisible) {
        if (isVisible) {
            if (!shouldShowWindow) {
                val w = currentWindowSize.width.value.toInt()
                val h = currentWindowSize.height.value.toInt()
                dialogState.position = getTrayWindowPositionForInstance(instanceKey, w, h)
                shouldShowWindow = true
                lastShownAt = System.currentTimeMillis()
            }
        } else {
            delay(fadeDurationMs.toLong())
            shouldShowWindow = false
            lastHiddenAt = System.currentTimeMillis()
        }
    }

    // Re-anchor on size change while visible (important for KWin/GNOME)
    LaunchedEffect(currentWindowSize, shouldShowWindow) {
        if (shouldShowWindow) {
            val w = currentWindowSize.width.value.toInt()
            val h = currentWindowSize.height.value.toInt()
            dialogState.position = getTrayWindowPositionForInstance(instanceKey, w, h)
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
        undecorated = true,
        resizable = false,
        focusable = shouldShowWindow, // avoid focus-steal while hidden
        alwaysOnTop = true,
        transparent = transparent,
        visible = shouldShowWindow,
        state = dialogState,
        onPreviewKeyEvent = onPreviewKeyEvent,
        onKeyEvent = onKeyEvent,
    ) {
        DisposableEffect(shouldShowWindow, dismissMode) {
            if (!shouldShowWindow) return@DisposableEffect onDispose { }

            // Ensure anchor once the AWT peer exists
            runCatching {
                val w = currentWindowSize.width.value.toInt()
                val h = currentWindowSize.height.value.toInt()
                dialogState.position = getTrayWindowPositionForInstance(instanceKey, w, h)
            }

            // Linux outside-click watcher
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

            onDispose {
                linuxWatcher?.stop()
            }
        }

        Box(
            modifier = Modifier.fillMaxSize().alpha(alpha)
        ) { content() }
    }
}
