package com.kdroid.composetray.tray.api

import androidx.compose.runtime.*
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * State holder for TrayApp that provides programmatic control over the tray window
 * and observability of its state changes.
 */
@ExperimentalTrayAppApi
class TrayAppState(
    initialWindowSize: DpSize = DpSize(300.dp, 200.dp),
    initiallyVisible: Boolean = false,
    dismissMode: TrayWindowDismissMode = TrayWindowDismissMode.AUTO
) {
    // Internal mutable state
    private val _isVisible = MutableStateFlow(initiallyVisible)
    private val _windowSize = MutableStateFlow(initialWindowSize)
    private val _dismissMode = MutableStateFlow(dismissMode)

    // Public observable state flows
    val isVisible: StateFlow<Boolean> = _isVisible.asStateFlow()
    val windowSize: StateFlow<DpSize> = _windowSize.asStateFlow()
    val dismissMode: StateFlow<TrayWindowDismissMode> = _dismissMode.asStateFlow()

    // Callbacks for visibility changes
    private var onVisibilityChanged: ((Boolean) -> Unit)? = null

    /**
     * Shows the tray window
     */
    fun show() {
        if (!_isVisible.value) {
            _isVisible.value = true
            onVisibilityChanged?.invoke(true)
        }
    }

    /**
     * Hides the tray window
     */
    fun hide() {
        if (_isVisible.value) {
            _isVisible.value = false
            onVisibilityChanged?.invoke(false)
        }
    }

    /**
     * Toggles the visibility of the tray window
     */
    fun toggle() {
        val newVisibility = !_isVisible.value
        _isVisible.value = newVisibility
        onVisibilityChanged?.invoke(newVisibility)
    }

    /**
     * Updates the window size
     * @param size The new window size
     */
    fun setWindowSize(size: DpSize) {
        _windowSize.value = size
    }

    /**
     * Updates the window size
     * @param width The new window width
     * @param height The new window height
     */
    fun setWindowSize(width: androidx.compose.ui.unit.Dp, height: androidx.compose.ui.unit.Dp) {
        _windowSize.value = DpSize(width, height)
    }

    /**
     * Updates the dismiss mode
     * @param mode The new dismiss mode
     */
    fun setDismissMode(mode: TrayWindowDismissMode) {
        _dismissMode.value = mode
    }

    /**
     * Sets a callback to be invoked when visibility changes
     * @param callback The callback to invoke with the new visibility state
     */
    fun onVisibilityChanged(callback: (Boolean) -> Unit) {
        onVisibilityChanged = callback
    }

    /**
     * Internal method to update visibility from within TrayApp
     * (e.g., when user clicks outside or closes the window)
     */
    internal fun updateVisibility(visible: Boolean) {
        if (_isVisible.value != visible) {
            _isVisible.value = visible
            onVisibilityChanged?.invoke(visible)
        }
    }
}

/**
 * Creates and remembers a TrayAppState instance
 * @param initialWindowSize The initial window size
 * @param initiallyVisible Whether the window should be initially visible
 * @param dismissMode How the window should be dismissed
 */
@ExperimentalTrayAppApi
@Composable
fun rememberTrayAppState(
    initialWindowSize: DpSize = DpSize(300.dp, 200.dp),
    initiallyVisible: Boolean = false,
    dismissMode: TrayWindowDismissMode = TrayWindowDismissMode.AUTO
): TrayAppState {
    return remember {
        TrayAppState(
            initialWindowSize = initialWindowSize,
            initiallyVisible = initiallyVisible,
            dismissMode = dismissMode
        )
    }
}