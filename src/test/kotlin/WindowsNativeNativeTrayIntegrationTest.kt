
import com.kdroid.composetray.tray.api.NativeTray
import org.junit.jupiter.api.Assertions.assertNotNull
import kotlin.test.Test


class NativeTrayIntegrationTest {

    @Test
    fun testNativeTrayMenuInteraction() {
        val trayIconPath = "/home/elyahou/CLionProjects/tray2/icon.png"

        val nativeTray = NativeTray(
            iconPath = trayIconPath,
            menuContent = {
                SubMenu(label = "Options") {
                    Item(label = "Setting 1") {
                        println("Setting 1 selected")
                    }
                    SubMenu(label = "Advanced Sub-options") {
                        Item(label = "Advanced Option 1") {
                            println("Advanced Option 1 selected")
                        }
                        Item(label = "Advanced Option 2") {
                            println("Advanced Option 2 selected")
                        }
                    }
                }

                Divider()

                SubMenu(label = "Tools") {
                    Item(label = "Calculator") {
                        println("Calculator launched")
                    }
                    Item(label = "Notepad") {
                        println("Notepad opened")
                    }
                }

                Divider()

                CheckableItem(label = "Enable notifications") { isChecked ->
                    println("Notifications ${if (isChecked) "enabled" else "disabled"}")
                }

                Divider()

                Item(label = "About") {
                    println("Application v1.0 - Developed by Elyahou")
                }

                Divider()

                Item(label = "Exit", isEnabled = true) {
                    println("Exiting the application")
                    dispose()
                     System.exit(0) // Commented to keep the application active
                }
            }
        )

        // Simulate user interactions
        assertNotNull(nativeTray)
        nativeTray.clickMenuItem("Options", "Setting 1")
        nativeTray.clickMenuItem("Options", "Advanced Sub-options", "Advanced Option 1")
        nativeTray.clickMenuItem("Options", "Advanced Sub-options", "Advanced Option 2")
        nativeTray.clickMenuItem("Tools", "Calculator")
        nativeTray.clickMenuItem("Tools", "Notepad")
        nativeTray.checkCheckableItem("Enable notifications", true)
        nativeTray.clickMenuItem("About")
    }
}

// Mock or utility functions for testing purposes
fun NativeTray.clickMenuItem(vararg labels: String) {
    println("Clicked on menu item: ${labels.joinToString(" -> ")}")
}

fun NativeTray.checkCheckableItem(label: String, isChecked: Boolean) {
    println("Checked item '$label' to: ${if (isChecked) "enabled" else "disabled"}")
}