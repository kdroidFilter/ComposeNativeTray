
import com.kdroid.composetray.tray.api.NativeTray
import kotlin.system.exitProcess

fun main() {
    val trayIconPath = "/home/elyahou/CLionProjects/tray2/icon.png"

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
                exitProcess(0)
            }
            Item(label = "Version 1.0.0", isEnabled = false)
        }
    )
}