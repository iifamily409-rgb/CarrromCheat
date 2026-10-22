package com.example.carromvision.overlay

import android.graphics.Color
import com.example.carromvision.domain.TrajectoryColor

/** Shared neon palette used by the aim renderer and the floating menu. */
object OverlayPalette {
    val CYAN = Color.rgb(0, 229, 255)
    val GOLD = Color.rgb(255, 209, 84)
    val WHITE = Color.WHITE
    val RED = Color.rgb(255, 68, 79)
    val BLUE = Color.rgb(70, 160, 255)
    val YELLOW = Color.rgb(255, 222, 62)
    val ORANGE = Color.rgb(255, 145, 45)
    val GREEN = Color.rgb(74, 230, 128)
    val PURPLE = Color.rgb(195, 110, 255)

    val lineChoices = intArrayOf(CYAN, GOLD, WHITE, RED, GREEN, PURPLE)
}

internal fun trajectoryColor(color: TrajectoryColor): Int = when (color) {
    TrajectoryColor.WHITE -> OverlayPalette.WHITE
    TrajectoryColor.RED -> OverlayPalette.RED
    TrajectoryColor.BLUE -> OverlayPalette.BLUE
    TrajectoryColor.YELLOW -> OverlayPalette.YELLOW
    TrajectoryColor.ORANGE -> OverlayPalette.ORANGE
    TrajectoryColor.GREEN -> OverlayPalette.GREEN
    TrajectoryColor.PURPLE -> OverlayPalette.PURPLE
}

internal fun withAlpha(color: Int, alpha: Int): Int = Color.argb(
    alpha.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color)
)

/** Mixes [color] toward white while preserving its neon hue. */
internal fun brighten(color: Int, amount: Float): Int {
    val t = amount.coerceIn(0f, 1f)
    fun channel(value: Int): Int = (value + (255 - value) * t).toInt().coerceIn(0, 255)
    return Color.rgb(channel(Color.red(color)), channel(Color.green(color)), channel(Color.blue(color)))
}
