package com.kdroid

import com.kdroid.kmplog.Log
import com.kdroid.kmplog.d
import com.kdroid.kmplog.i
import com.kdroid.lib.linux.Gtk
import com.kdroid.state.rememberNotification
import com.kdroid.state.rememberTrayState
import com.kdroid.tray.Tray

fun main() {
    val trayState = rememberTrayState()
    val notification = rememberNotification("Notification", "Message from MyApp!")
    val trayIconPath = "/home/elyahou/Images/avatar.jpeg"

     Tray(
        state = trayState,
        icon = trayIconPath,
        menuContent = {
            Item("Increment value") {
                Log.i("Increment","Increment clicked")
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
                dispose()
            }
        }
    )
}