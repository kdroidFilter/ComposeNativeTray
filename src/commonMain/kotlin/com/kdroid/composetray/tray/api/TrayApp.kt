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

// --------------------- Overloads (same public API) ---------------------

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
                ?: if (isDark) ColorFilter.tint(Color.White)
                else ColorFilter.tint(Color.Black)
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

// --------------------- Core TrayApp (iconContent; no density) ---------------------

/**
 * Core overload that always delegates placement to your function
 * getTrayWindowPositionForInstance(tray.instanceKey(), widthPx, heightPx)
 * and never performs DP→px conversions.
 *
 * Fixes:
 *  - Seed the initial position BEFORE the first frame (KWin cares about first placement).
 *  - Always set position right before show (even if it looks the same).
 *  - Re-anchor when the AWT Window exists (in DisposableEffect at show-time).
 */
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
    // State holder (keeps the window mounted; we toggle 'visible')
    val trayAppState = state ?: rememberTrayAppState(
        initialWindowSize = windowSize ?: DpSize(300.dp, 200.dp),
        initiallyVisible = visibleOnStart,
        initialDismissMode = TrayWindowDismissMode.AUTO
    )

    val isVisible by trayAppState.isVisible.collectAsState()
    val currentWindowSize by trayAppState.windowSize.collectAsState()
    val dismissMode by trayAppState.dismissMode.collectAsState()

    // Native tray instance and a stable instance key
    val tray = remember { NativeTray() }
    val instanceKey = remember { tray.instanceKey() }

    // Icon/materialization
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

    // Fade animation
    val alpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = animationSpec,
        label = "window_fade"
    )

    // Local visibility/timing guards
    var shouldShowWindow by remember { mutableStateOf(false) }
    var lastPrimaryActionAt by remember { mutableStateOf(0L) }
    val toggleDebounceMs = 280L
    var lastShownAt by remember { mutableStateOf(0L) }
    var lastHiddenAt by remember { mutableStateOf(0L) }
    val minVisibleDurationMs = 350L
    val minHiddenDurationMs = 250L
    var lastFocusLostAt by remember { mutableStateOf(0L) }
    var autoHideEnabledAt by remember { mutableStateOf(0L) }

    // ---- Initial position: compute BEFORE DialogWindow is created (no density) ----
    val initialPositionForFirstFrame = remember(instanceKey, currentWindowSize) {
        val widthPx = currentWindowSize.width.value.toInt()
        val heightPx = currentWindowSize.height.value.toInt()
        getTrayWindowPositionForInstance(instanceKey, widthPx, heightPx)
    }

    // Persistent dialog state seeded with the correct initial position
    val dialogState = rememberDialogState(
        position = initialPositionForFirstFrame,
        size = currentWindowSize
    )

    // Ensure the first-frame position is actually written to the underlying AWT window
    SideEffect {
        dialogState.position = initialPositionForFirstFrame
    }

    // Keep dialog size in sync
    LaunchedEffect(currentWindowSize) { dialogState.size = currentWindowSize }

    // Explicit hide with min-visible guard
    val requestHideExplicit: () -> Unit = {
        val now = System.currentTimeMillis()
        val sinceShow = now - lastShownAt
        if (sinceShow >= minVisibleDurationMs) {
            trayAppState.hide()
            lastHiddenAt = now
        } else {
            val wait = (minVisibleDurationMs - sinceShow).coerceAtLeast(0)
            CoroutineScope(Dispatchers.IO).launch {
                delay(wait.toLong())
                trayAppState.hide()
                lastHiddenAt = System.currentTimeMillis()
            }
        }
    }

    // Primary tray action: pre-position using your function, then show
    val internalPrimaryAction: () -> Unit = action@{
        val now = System.currentTimeMillis()
        if (now - lastPrimaryActionAt < toggleDebounceMs) return@action
        lastPrimaryActionAt = now

        if (isVisible) {
            requestHideExplicit()
        } else if (now - lastHiddenAt >= minHiddenDurationMs) {
            val widthPx = currentWindowSize.width.value.toInt()
            val heightPx = currentWindowSize.height.value.toInt()
            val pre = getTrayWindowPositionForInstance(instanceKey, widthPx, heightPx)
            // Always apply the pre-computed position before showing
            dialogState.position = pre
            trayAppState.show()
        }
    }

    // React to isVisible changes; position FIRST, then make visible
    LaunchedEffect(isVisible) {
        if (isVisible) {
            if (!shouldShowWindow) {
                val widthPx = currentWindowSize.width.value.toInt()
                val heightPx = currentWindowSize.height.value.toInt()
                dialogState.position = getTrayWindowPositionForInstance(
                    instanceKey, widthPx, heightPx
                )
                if (getOperatingSystem() == WINDOWS) {
                    // avoid early outside-click auto-dismiss right after focus loss
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

    // If size changes while visible, re-anchor with your function (still no density)
    LaunchedEffect(currentWindowSize, shouldShowWindow) {
        if (shouldShowWindow) {
            val widthPx = currentWindowSize.width.value.toInt()
            val heightPx = currentWindowSize.height.value.toInt()
            dialogState.position = getTrayWindowPositionForInstance(
                instanceKey, widthPx, heightPx
            )
        }
    }

    // Update tray icon/menu
    LaunchedEffect(pngIconPath, windowsIconPath, tooltip, internalPrimaryAction, menu, contentHash, menuHash) {
        tray.update(pngIconPath, windowsIconPath, tooltip, internalPrimaryAction, menu)
    }

    // macOS dock visibility follow-up
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

    // === Popup window (always mounted; visibility toggled) ===
    DialogWindow(
        // Closing via OS/ESC is explicit user intent → allowed in MANUAL too
        onCloseRequest = { requestHideExplicit() },
        title = "",
        undecorated = true,
        resizable = false,
        focusable = shouldShowWindow,
        alwaysOnTop = true,
        transparent = true,
        visible = shouldShowWindow,
        state = dialogState,
    ) {
        // Attach/Detach platform listeners only while visible OR when mode changes.
        DisposableEffect(shouldShowWindow, dismissMode) {
            if (!shouldShowWindow) {
                return@DisposableEffect onDispose { }
            }

            // Re-anchor exactly when the AWT Window exists to defeat KWin races
            runCatching {
                val widthPx  = currentWindowSize.width.value.toInt()
                val heightPx = currentWindowSize.height.value.toInt()
                dialogState.position = getTrayWindowPositionForInstance(
                    instanceKey, widthPx, heightPx
                )
            }

            // Mark as tray popup (macOS visibility monitor)
            try { window.name = WindowVisibilityMonitor.TRAY_DIALOG_NAME } catch (_: Throwable) {}
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
            val osNow = getOperatingSystem()
            val focusListener = object : WindowFocusListener {
                override fun windowGainedFocus(e: WindowEvent?) = Unit
                override fun windowLostFocus(e: WindowEvent?) {
                    lastFocusLostAt = System.currentTimeMillis()
                    if (osNow == WINDOWS && lastFocusLostAt < autoHideEnabledAt) return
                    if (dismissMode == TrayWindowDismissMode.AUTO) {
                        requestHideExplicit()
                    }
                }
            }

            // Outside click watchers: start them only in AUTO mode
            val macWatcher = if (dismissMode == TrayWindowDismissMode.AUTO && osNow == MACOS) {
                MacOutsideClickWatcher(
                    windowSupplier = { window },
                    onOutsideClick = { invokeLater { requestHideExplicit() } }
                ).also { it.start() }
            } else null

            val linuxWatcher = if (dismissMode == TrayWindowDismissMode.AUTO && osNow == OperatingSystem.LINUX) {
                LinuxOutsideClickWatcher(
                    windowSupplier = { window },
                    onOutsideClick = { invokeLater { requestHideExplicit() } }
                ).also { it.start() }
            } else null

            val windowsWatcher = if (dismissMode == TrayWindowDismissMode.AUTO && osNow == WINDOWS) {
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

        // Content wrapper with fade animation (state preserved across show/hide)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(alpha)
        ) { content() }
    }
}
