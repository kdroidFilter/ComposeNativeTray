package com.kdroid.composetray.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.currentComposer
import com.kdroid.composetray.menu.api.TrayMenuBuilder
import java.security.MessageDigest

/**
 * Utility class for calculating a hash of menu content to detect changes
 */
object MenuContentHash {

    /**
     * Calculates a hash of the menu content by capturing the menu structure
     * This function should be called from a @Composable context to track state changes
     */
    @Composable
    fun calculateMenuHash(menuContent: (TrayMenuBuilder.() -> Unit)?): String {
        if (menuContent == null) return "empty"

        // Create a capturing menu builder that records all operations
        val capturingBuilder = CapturingMenuBuilder()

        // Execute the menu content to capture the current state
        // This will automatically recompose when any @Composable state used inside changes
        menuContent.invoke(capturingBuilder)

        // Generate hash from captured operations
        return capturingBuilder.generateHash()
    }

    /**
     * A TrayMenuBuilder implementation that captures all menu operations
     * to generate a hash representing the menu structure
     */
    private class CapturingMenuBuilder : TrayMenuBuilder {
        private val operations = mutableListOf<String>()

        override fun Item(label: String, isEnabled: Boolean, onClick: () -> Unit) {
            operations.add("Item:$label:$isEnabled")
        }

        override fun CheckableItem(
            label: String,
            checked: Boolean,
            onCheckedChange: (Boolean) -> Unit,
            isEnabled: Boolean
        ) {
            operations.add("CheckableItem:$label:$checked:$isEnabled")
        }

        override fun SubMenu(
            label: String,
            isEnabled: Boolean,
            submenuContent: (TrayMenuBuilder.() -> Unit)?
        ) {
            operations.add("SubMenu:$label:$isEnabled")
            if (submenuContent != null) {
                operations.add("SubMenuStart")
                submenuContent.invoke(this)
                operations.add("SubMenuEnd")
            }
        }

        override fun Divider() {
            operations.add("Divider")
        }

        override fun dispose() {
            // Not needed for capturing
        }

        fun generateHash(): String {
            val content = operations.joinToString("|")
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(content.toByteArray())
            return digest.fold("") { str, it -> str + "%02x".format(it) }.take(16) // Use only first 16 chars for performance
        }
    }
}