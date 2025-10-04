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
 * TrayApp – state-preserving tray popup with platform-tuned anchoring.
 *
 * Linux and macOS behave differently on initial placement:
 *  - macOS/Windows: the original logic (delayed first frame + polling until a non-default
 *    anchor) works reliably with NSStatusItem and Win tray.
 *  - Linux (GNOME/KDE/etc.): the newer approach (pre-seed DialogState.position, re-apply
 *    right before show, and re-anchor while visible) is more robust against KWin/GNOME races.
 *
 * This file keeps one public API but internally routes to platform-specific implementations:
 *  - Linux  -> ImplLinux (new logic)
 *  - mac/Win -> ImplOriginal (previous logic)
 *
 * All code comments are in English by user preference.
 */

// --------------------- Overloads (public API kept stable) ---------------------

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
    fadeDurationMs: Int = 200,
    animationSpec: AnimationSpec<Float> = tween(durationMillis = fadeDurationMs, easing = EaseInOut),
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
    state: TrayAppState? = null,
    windowSize: DpSize? = null,
    visibleOnStart: Boolean = false,
    fadeDurationMs: Int = 200,
    animationSpec: AnimationSpec<Float> = tween(durationMillis = fadeDurationMs, easing = EaseInOut),
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
    menu: (TrayMenuBuilder.() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    when (getOperatingSystem()) {
        OperatingSystem.LINUX -> TrayAppImplLinux(
            iconContent, iconRenderProperties, tooltip, state, windowSize,
            visibleOnStart, fadeDurationMs, animationSpec, menu, content
        )
        else -> TrayAppImplOriginal(
            iconContent, iconRenderProperties, tooltip, state, windowSize,
            visibleOnStart, fadeDurationMs, animationSpec, menu, content
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

    DialogWindow(
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

    // Helper window
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

    DialogWindow(
        onCloseRequest = { requestHideExplicit() },
        title = "",
        undecorated = true,
        resizable = false,
        focusable = shouldShowWindow, // avoid focus-steal while hidden
        alwaysOnTop = true,
        transparent = true,
        visible = shouldShowWindow,
        state = dialogState,
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
