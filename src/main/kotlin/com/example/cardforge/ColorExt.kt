package com.example.cardforge
import javafx.scene.paint.Color
fun Color.toHex(): String = String.format("#%02X%02X%02X", (red*255).toInt(), (green*255).toInt(), (blue*255).toInt())
