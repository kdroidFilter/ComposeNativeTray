package com.kdroid.composetray.tray.api

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ExperimentalComposeApi
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * State holder for TrayApp that provides programmatic control over the tray window
 * and observability of its state changes, including window dismiss behavior.
 */
@ExperimentalTrayAppApi
class TrayAppState(
    initialWindowSize: DpSize = DpSize(300.dp, 200.dp),
    initiallyVisible: Boolean = false,
    initialDismissMode: TrayWindowDismissMode = TrayWindowDismissMode.AUTO,
) {
    // Internal mutable state
    private val _isVisible = MutableStateFlow(initiallyVisible)
    private val _windowSize = MutableStateFlow(initialWindowSize)
    private val _dismissMode = MutableStateFlow(initialDismissMode)

    // Public observable state flows
    val isVisible: StateFlow<Boolean> = _isVisible.asStateFlow()
    val windowSize: StateFlow<DpSize> = _windowSize.asStateFlow()
    val dismissMode: StateFlow<TrayWindowDismissMode> = _dismissMode.asStateFlow()

    // Callback for visibility changes
    private var onVisibilityChanged: ((Boolean) -> Unit)? = null

    /** Shows the tray window */
    fun show() {
        if (!_isVisible.value) {
            _isVisible.value = true
            onVisibilityChanged?.invoke(true)
        }
    }

    /** Hides the tray window (explicit hide, works in any dismiss mode) */
    fun hide() {
        if (_isVisible.value) {
            _isVisible.value = false
            onVisibilityChanged?.invoke(false)
        }
    }

    /** Toggles the visibility of the tray window */
    fun toggle() {
        val newVisibility = !_isVisible.value
        _isVisible.value = newVisibility
        onVisibilityChanged?.invoke(newVisibility)
    }

    /** Updates the window size */
    fun setWindowSize(size: DpSize) {
        _windowSize.value = size
    }

    /** Updates the window size */
    fun setWindowSize(width: androidx.compose.ui.unit.Dp, height: androidx.compose.ui.unit.Dp) {
        _windowSize.value = DpSize(width, height)
    }

    /** Updates the dismiss mode (AUTO or MANUAL) */
    fun setDismissMode(mode: TrayWindowDismissMode) {
        _dismissMode.value = mode
    }

    /** Sets a callback to be invoked when visibility changes */
    fun onVisibilityChanged(callback: (Boolean) -> Unit) {
        onVisibilityChanged = callback
    }

    /** Internal method to update visibility from TrayApp internals */
    internal fun updateVisibility(visible: Boolean) {
        if (_isVisible.value != visible) {
            _isVisible.value = visible
            onVisibilityChanged?.invoke(visible)
        }
    }
}

/** Creates and remembers a TrayAppState instance */
@ExperimentalTrayAppApi
@Composable
fun rememberTrayAppState(
    initialWindowSize: DpSize = DpSize(300.dp, 200.dp),
    initiallyVisible: Boolean = false,
    initialDismissMode: TrayWindowDismissMode = TrayWindowDismissMode.AUTO,
): TrayAppState {
    return remember {
        TrayAppState(
            initialWindowSize = initialWindowSize,
            initiallyVisible = initiallyVisible,
            initialDismissMode = initialDismissMode
        )
    }
}
