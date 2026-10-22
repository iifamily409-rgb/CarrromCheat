package com.example.carromvision.overlay

import com.example.carromvision.domain.BoardSpec
import com.example.carromvision.domain.Vector2D

data class BoardViewport(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 1f,
    val bottom: Float = 1f,
    val logicalSize: Float = BoardSpec.LOGICAL_SIZE
)

internal class Projection {
    private var originX = 0f
    private var originY = 0f
    private var scaleX = 1f
    private var scaleY = 1f

    fun update(originX: Float, originY: Float, scaleX: Float, scaleY: Float) {
        this.originX = originX
        this.originY = originY
        this.scaleX = scaleX
        this.scaleY = scaleY
    }

    fun x(point: Vector2D): Float = originX + point.x * scaleX
    fun y(point: Vector2D): Float = originY + point.y * scaleY
}
