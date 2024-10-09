package com.kdroid.composetray.state

class Notification(private val title: String, private val message: String) {
    fun show() {
        // Utilisez libnotify via JNA pour afficher la notification
        // Exemple simplifié :
        Runtime.getRuntime().exec("notify-send '$title' '$message'")
        println(title + message)
    }
}

fun rememberNotification(title: String, message: String): Notification =
    Notification(title, message)
