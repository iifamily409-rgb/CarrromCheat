package com.example.carromvision.overlay

data class OverlayConfig(
    val lineColor: Int = OverlayPalette.CYAN,
    val glowThickness: Float = 1.0f,
    val puckRadiusMultiplier: Float = 1.0f,
    val strikerRadiusMultiplier: Float = 1.0f,
    val multiLineEnabled: Boolean = false,
    val bankLinesEnabled: Boolean = true
)
