package com.example.carromvision.overlay

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.example.carromvision.domain.Vector2D
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** Specular cushion markers and path arrow heads for the aim canvas. */
internal class CanvasDecorations(private val density: Float) {
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    fun drawBounces(
        canvas: Canvas,
        projection: Projection,
        points: List<Vector2D>,
        color: Int
    ) {
        if (points.size <= 2) return
        val outer = 6.2f * density
        for (index in 1 until points.size - 1) {
            val x = projection.x(points[index])
            val y = projection.y(points[index])
            fillPaint.color = withAlpha(color, 30)
            canvas.drawCircle(x, y, outer * 1.8f, fillPaint)
            linePaint.color = withAlpha(color, 220)
            linePaint.strokeWidth = 1.3f * density
            linePaint.pathEffect = null
            canvas.drawCircle(x, y, outer, linePaint)
            linePaint.color = Color.WHITE
            linePaint.strokeWidth = 1.0f * density
            canvas.drawCircle(x, y, outer * 0.42f, linePaint)
            linePaint.color = withAlpha(color, 150)
            canvas.drawLine(x - outer * 1.5f, y, x + outer * 1.5f, y, linePaint)
            canvas.drawLine(x, y - outer * 1.5f, x, y + outer * 1.5f, linePaint)
        }
    }

    fun drawArrowHead(
        canvas: Canvas,
        projection: Projection,
        points: List<Vector2D>,
        color: Int
    ) {
        if (points.size < 2) return
        val tip = points.last()
        val previous = points[points.lastIndex - 1]
        val dx = projection.x(tip) - projection.x(previous)
        val dy = projection.y(tip) - projection.y(previous)
        if (dx == 0f && dy == 0f) return
        val angle = atan2(dy, dx)
        val length = 11f * density
        linePaint.color = color
        linePaint.strokeWidth = 2.2f * density
        linePaint.pathEffect = null
        for (side in intArrayOf(-1, 1)) {
            val wing = angle + side * ARROW_SPREAD
            canvas.drawLine(
                projection.x(tip), projection.y(tip),
                projection.x(tip) - cos(wing) * length,
                projection.y(tip) - sin(wing) * length,
                linePaint
            )
        }
    }

    private companion object {
        const val ARROW_SPREAD = 0.42f
    }
}
