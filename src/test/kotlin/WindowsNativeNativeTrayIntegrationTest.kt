import com.kdroid.composetray.lib.linux.Gtk
import com.kdroid.composetray.state.rememberNotification
import com.kdroid.composetray.state.rememberTrayState
import kotlin.test.Test

class WindowsNativeNativeTrayIntegrationTest {
    @Test
    fun `test Tray integration`() {
        // Arrange
        val trayState = rememberTrayState()
        val notification = rememberNotification("Notification", "Message from MyApp!")
        val trayIconPath = "/home/elyahou/Images/avatar.jpeg"

        // Lancer le tray dans un thread séparé car gtk_main est bloquant

            WindowsNativeTray(
                state = trayState,
                icon = trayIconPath,
                menuContent = {
                    Item("Increment value") {
                        println("Increment clicked")
                    }
                    Item("Send notification") {
                        trayState.sendNotification(notification)
                    }
                    Item("Disabled Item", isEnabled = false) {}
                    CheckableItem("Toggle me") {
                        println("Toggle me clicked")
                    }
                    Divider()
                    SubMenu("SubMenu") {
                        Item("Option 1") {
                            // Action
                        }
                        Item("Option 2") {
                            // Action
                        }
                        SubMenu("SubSubMenu") {
                            Item("Option 3") {}
                        }
                    }
                    Item("Exit") {
                        println("Exit clicked")
                        Gtk.INSTANCE.gtk_main_quit()  // Arrête la boucle GTK proprement
                    }
                }
            )


    }
}