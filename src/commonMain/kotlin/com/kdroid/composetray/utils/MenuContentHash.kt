package com.kdroid.composetray.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.currentComposer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import com.kdroid.composetray.menu.api.TrayMenuBuilder
import com.kdroid.composetray.utils.IconRenderProperties
import org.jetbrains.compose.resources.DrawableResource
import java.security.MessageDigest
import io.github.kdroidfilter.platformtools.darkmodedetector.isSystemInDarkMode

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

        // Include system/menu bar dark mode in the hash seed so a theme switch triggers recomposition
        val isDark = isMenuBarInDarkMode()
        val isSystemDark = isSystemInDarkMode()

        // Create a capturing menu builder that records all operations
        val capturingBuilder = CapturingMenuBuilder()

        // Execute the menu content to capture the current state
        // This will automatically recompose when any @Composable state used inside changes
        menuContent.invoke(capturingBuilder)

        // Generate hash from captured operations + theme state to reflect dark mode changes
        return capturingBuilder.generateHash(seed = isDark.hashCode() + isSystemDark.hashCode())
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
        
        override fun Item(
            label: String,
            iconContent: @Composable () -> Unit,
            iconRenderProperties: IconRenderProperties,
            isEnabled: Boolean,
            onClick: () -> Unit
        ) {
            val identity = System.identityHashCode(iconContent)
            operations.add("ItemWithComposableIcon:$label:$isEnabled:$identity")
        }
        
        override fun Item(
            label: String,
            icon: ImageVector,
            iconTint: Color?,
            iconRenderProperties: IconRenderProperties,
            isEnabled: Boolean,
            onClick: () -> Unit
        ) {
            operations.add("ItemWithImageVectorIcon:$label:${iconTint != null}:$isEnabled:${icon.hashCode()}")
        }
        
        override fun Item(
            label: String,
            icon: Painter,
            iconRenderProperties: IconRenderProperties,
            isEnabled: Boolean,
            onClick: () -> Unit
        ) {
            operations.add("ItemWithPainterIcon:$label:$isEnabled:${icon.hashCode()}")
        }

        override fun Item(
            label: String,
            icon: DrawableResource,
            iconRenderProperties: IconRenderProperties,
            isEnabled: Boolean,
            onClick: () -> Unit
        ) {
            operations.add("ItemWithDrawableIcon:$label:$isEnabled:${icon.hashCode()}")
        }

        override fun CheckableItem(
            label: String,
            checked: Boolean,
            onCheckedChange: (Boolean) -> Unit,
            isEnabled: Boolean
        ) {
            operations.add("CheckableItem:$label:$checked:$isEnabled")
        }
        
        override fun CheckableItem(
            label: String,
            iconContent: @Composable () -> Unit,
            iconRenderProperties: IconRenderProperties,
            checked: Boolean,
            onCheckedChange: (Boolean) -> Unit,
            isEnabled: Boolean
        ) {
            val identity = System.identityHashCode(iconContent)
            operations.add("CheckableItemWithComposableIcon:$label:$checked:$isEnabled:$identity")
        }
        
        override fun CheckableItem(
            label: String,
            icon: ImageVector,
            iconTint: Color?,
            iconRenderProperties: IconRenderProperties,
            checked: Boolean,
            onCheckedChange: (Boolean) -> Unit,
            isEnabled: Boolean
        ) {
            operations.add("CheckableItemWithImageVectorIcon:$label:${iconTint != null}:$checked:$isEnabled:${icon.hashCode()}")
        }
        
        override fun CheckableItem(
            label: String,
            icon: Painter,
            iconRenderProperties: IconRenderProperties,
            checked: Boolean,
            onCheckedChange: (Boolean) -> Unit,
            isEnabled: Boolean
        ) {
            operations.add("CheckableItemWithPainterIcon:$label:$checked:$isEnabled:${icon.hashCode()}")
        }

        override fun CheckableItem(
            label: String,
            icon: DrawableResource,
            iconRenderProperties: IconRenderProperties,
            checked: Boolean,
            onCheckedChange: (Boolean) -> Unit,
            isEnabled: Boolean
        ) {
            operations.add("CheckableItemWithDrawableIcon:$label:$checked:$isEnabled:${icon.hashCode()}")
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
        
        override fun SubMenu(
            label: String,
            iconContent: @Composable () -> Unit,
            iconRenderProperties: IconRenderProperties,
            isEnabled: Boolean,
            submenuContent: (TrayMenuBuilder.() -> Unit)?
        ) {
            val identity = System.identityHashCode(iconContent)
            operations.add("SubMenuWithComposableIcon:$label:$isEnabled:$identity")
            if (submenuContent != null) {
                operations.add("SubMenuStart")
                submenuContent.invoke(this)
                operations.add("SubMenuEnd")
            }
        }
        
        override fun SubMenu(
            label: String,
            icon: ImageVector,
            iconTint: Color?,
            iconRenderProperties: IconRenderProperties,
            isEnabled: Boolean,
            submenuContent: (TrayMenuBuilder.() -> Unit)?
        ) {
            operations.add("SubMenuWithImageVectorIcon:$label:${iconTint != null}:$isEnabled:${icon.hashCode()}")
            if (submenuContent != null) {
                operations.add("SubMenuStart")
                submenuContent.invoke(this)
                operations.add("SubMenuEnd")
            }
        }
        
        override fun SubMenu(
            label: String,
            icon: Painter,
            iconRenderProperties: IconRenderProperties,
            isEnabled: Boolean,
            submenuContent: (TrayMenuBuilder.() -> Unit)?
        ) {
            operations.add("SubMenuWithPainterIcon:$label:$isEnabled:${icon.hashCode()}")
            if (submenuContent != null) {
                operations.add("SubMenuStart")
                submenuContent.invoke(this)
                operations.add("SubMenuEnd")
            }
        }

        override fun SubMenu(
            label: String,
            icon: DrawableResource,
            iconRenderProperties: IconRenderProperties,
            isEnabled: Boolean,
            submenuContent: (TrayMenuBuilder.() -> Unit)?
        ) {
            operations.add("SubMenuWithDrawableIcon:$label:$isEnabled:${icon.hashCode()}")
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

        fun generateHash(seed: Int = 0): String {
            val content = operations.joinToString("|") + "|theme=" + seed
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(content.toByteArray())
            return digest.fold("") { str, it -> str + "%02x".format(it) }.take(16) // Use only first 16 chars for performance
        }
    }
}