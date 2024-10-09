import com.kdroid.lib.linux.Gtk
import com.kdroid.state.rememberNotification
import com.kdroid.state.rememberTrayState
import com.kdroid.tray.Tray
import kotlin.test.Test

class TrayIntegrationTest {
    @Test
    fun `test Tray integration`() {
        // Arrange
        val trayState = rememberTrayState()
        val notification = rememberNotification("Notification", "Message from MyApp!")
        val trayIconPath = "/home/elyahou/Images/avatar.jpeg"

        // Lancer le tray dans un thread séparé car gtk_main est bloquant
        val thread = Thread {
            Tray(
                state = trayState,
                icon = trayIconPath,
                menuContent = {
                    Item("Increment value") {
                        println("Increment clicked")
                    }
                    Item("Send notification") {
                        trayState.sendNotification(notification)
                    }
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
        thread.start()

        Thread.sleep(10000)
    }
}