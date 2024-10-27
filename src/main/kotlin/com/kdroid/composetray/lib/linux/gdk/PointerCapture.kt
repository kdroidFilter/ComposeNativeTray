package com.kdroid.composetray.lib.linux.gdk

fun convertPositionToCorner(x: Int, y: Int, width: Int, height: Int): String {
    return when {
        x < width / 2 && y < height / 2 -> "haut gauche"
        x >= width / 2 && y < height / 2 -> "haut droite"
        x < width / 2 && y >= height / 2 -> "bas gauche"
        else -> "bas droite"
    }
}