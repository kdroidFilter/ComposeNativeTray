package com.kdroid.state

class TrayState {
    fun sendNotification(notification: Notification) {
        // Implémentez l'envoi de notification via libnotify ou un autre mécanisme
        notification.show()
    }
}

fun rememberTrayState(): TrayState = TrayState()

