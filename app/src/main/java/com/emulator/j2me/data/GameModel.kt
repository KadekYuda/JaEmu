package com.emulator.j2me.data

data class GameModel(
    val id: String,
    val name: String,
    val vendor: String,
    val version: String,
    val mainClass: String,
    val jarPath: String,
    val dexPath: String,
    val iconPath: String?,
    val sizeBytes: Long,
    var targetWidth: Int = 240,
    var targetHeight: Int = 320,
    var keypadOpacity: Float = 0.6f,
    var scaleMode: String = "FIT" 
)
