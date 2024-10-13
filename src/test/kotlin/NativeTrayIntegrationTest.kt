
import com.kdroid.composetray.tray.api.NativeTray
import java.nio.file.Paths
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertTrue

class NativeTrayIntegrationTest {

    @Test
    fun testNativeTrayMenuInteraction() {
        val trayIconPath = Paths.get("src/test/resources/icon.ico").toAbsolutePath().toString()
        val testCompletionLatch = CountDownLatch(1)

        NativeTray(
            iconPath = trayIconPath,
            tooltip = "My Application",
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
                    testCompletionLatch.countDown() // Signal that the test can complete
                }

                Item(label = "Version 1.0.0", isEnabled = false)
            }
        )

        // Wait for the "Exit" item to be clicked, with a timeout to avoid indefinite blocking
        val completed = testCompletionLatch.await(10, TimeUnit.SECONDS)
        assertTrue(completed, "The application did not exit as expected")
    }
}