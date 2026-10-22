package com.example.carromvision.overlay

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import com.example.carromvision.domain.BoardSpec
import com.example.carromvision.domain.ShotTrajectory
import com.example.carromvision.domain.Vector2D

/**
 * Stateless-ish painter for the transparent aim canvas. All geometry arrives in
 * 1000 x 1000 logical board units and is projected through one isotropic scale,
 * so circles stay circular regardless of the captured screen aspect.
 */
internal class TrajectoryPainter(private val density: Float) {
    var config: OverlayConfig = OverlayConfig()
    private val projection = Projection()
    private val path = Path()
    private val dash = DashPathEffect(floatArrayOf(12f * density, 9f * density), 0f)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val decorations = CanvasDecorations(density)
    private var logicalSize = BoardSpec.LOGICAL_SIZE

    fun draw(
        canvas: Canvas,
        viewWidth: Int,
        viewHeight: Int,
        viewport: BoardViewport,
        primary: ShotTrajectory,
        assists: List<ShotTrajectory>
    ) {
        val boardWidth = (viewport.right - viewport.left) * viewWidth
        val boardHeight = (viewport.bottom - viewport.top) * viewHeight
        if (boardWidth < 1f || boardHeight < 1f) return
        val logical = viewport.logicalSize.coerceAtLeast(1f)
        logicalSize = logical
        val side = minOf(boardWidth, boardHeight)
        projection.update(
            viewport.left * viewWidth + (boardWidth - side) / 2f,
            viewport.top * viewHeight + (boardHeight - side) / 2f,
            side / logical,
            side / logical
        )

        if (config.multiLineEnabled) {
            for (assist in assists) {
                if (assist.isBankShot && !config.bankLinesEnabled) continue
                if (sameTarget(assist, primary)) continue
                drawAssist(canvas, assist, side)
            }
        }
        drawPrimary(canvas, primary, side)
    }

    private fun drawPrimary(canvas: Canvas, shot: ShotTrajectory, boardMin: Float) {
        drawNeon(canvas, shot.strikerPath, config.lineColor, 3.6f, 10f)
        decorations.drawBounces(canvas, projection, shot.strikerPath, config.lineColor)
        if (shot.coinPath.size >= 2) {
            drawNeon(canvas, shot.coinPath, OverlayPalette.GOLD, 2.6f, 7f)
            decorations.drawArrowHead(canvas, projection, shot.coinPath, OverlayPalette.GOLD)
            decorations.drawBounces(canvas, projection, shot.coinPath, OverlayPalette.GOLD)
        }
        if (shot.strikerDeflection.size >= 2) {
            drawLine(canvas, shot.strikerDeflection, Color.WHITE, 1.4f, 0f, true)
        }

        drawGhost(canvas, shot.ghostBallCenter, boardMin)
        drawRing(
            canvas, shot.targetCoinPos, boardMin,
            BoardSpec.COIN_RADIUS * config.puckRadiusMultiplier,
            OverlayPalette.WHITE, 1.8f
        )
        if (shot.coinPath.size >= 2) {
            drawRing(
                canvas, shot.pocketTarget, boardMin,
                BoardSpec.POCKET_RADIUS * config.puckRadiusMultiplier,
                OverlayPalette.GOLD, 2.2f
            )
        }
        drawRing(
            canvas, shot.strikerStart, boardMin,
            BoardSpec.STRIKER_RADIUS * config.strikerRadiusMultiplier,
            withAlpha(config.lineColor, 215), 1.6f
        )
    }

    private fun drawAssist(canvas: Canvas, shot: ShotTrajectory, boardMin: Float) {
        val color = trajectoryColor(shot.color)
        drawNeon(canvas, shot.strikerPath, color, 1.9f, 5f)
        decorations.drawBounces(canvas, projection, shot.strikerPath, color)
        if (shot.coinPath.size >= 2) {
            drawNeon(canvas, shot.coinPath, color, 2.0f, 4f)
            decorations.drawBounces(canvas, projection, shot.coinPath, color)
            drawRing(
                canvas, shot.pocketTarget, boardMin,
                BoardSpec.POCKET_RADIUS * config.puckRadiusMultiplier,
                color, 1.5f
            )
        }
        drawRing(
            canvas, shot.targetCoinPos, boardMin,
            BoardSpec.COIN_RADIUS * config.puckRadiusMultiplier,
            color, 1.5f
        )
    }

    private fun drawNeon(
        canvas: Canvas,
        points: List<Vector2D>,
        color: Int,
        widthDp: Float,
        glowDp: Float
    ) {
        if (points.size < 2) return
        buildPath(points)
        val glow = glowDp * config.glowThickness * density
        glowPaint.color = withAlpha(color, 40)
        glowPaint.strokeWidth = widthDp * density + glow
        glowPaint.pathEffect = null
        canvas.drawPath(path, glowPaint)
        glowPaint.color = withAlpha(color, 95)
        glowPaint.strokeWidth = widthDp * density + glow * 0.45f
        canvas.drawPath(path, glowPaint)
        linePaint.color = brighten(color, 0.55f)
        linePaint.strokeWidth = widthDp * density
        linePaint.pathEffect = null
        canvas.drawPath(path, linePaint)
    }

    private fun drawLine(
        canvas: Canvas,
        points: List<Vector2D>,
        color: Int,
        widthDp: Float,
        glowDp: Float,
        dashed: Boolean
    ) {
        if (points.size < 2) return
        buildPath(points)
        linePaint.color = color
        linePaint.strokeWidth =
            (widthDp * density) + glowDp * config.glowThickness * density
        linePaint.pathEffect = if (dashed) dash else null
        canvas.drawPath(path, linePaint)
        linePaint.pathEffect = null
    }

    private fun drawGhost(canvas: Canvas, center: Vector2D, boardMin: Float) {
        val x = projection.x(center)
        val y = projection.y(center)
        val radius = radiusToPx(BoardSpec.COIN_RADIUS, boardMin) *
            config.puckRadiusMultiplier
        fillPaint.color = withAlpha(config.lineColor, 46)
        canvas.drawCircle(x, y, radius, fillPaint)
        linePaint.color = withAlpha(config.lineColor, 235)
        linePaint.strokeWidth = 2.4f * density
        linePaint.pathEffect = null
        canvas.drawCircle(x, y, radius, linePaint)
        linePaint.color = Color.WHITE
        linePaint.strokeWidth = 1.2f * density
        canvas.drawCircle(x, y, radius * 0.42f, linePaint)
        canvas.drawLine(x - radius, y, x + radius, y, linePaint)
        canvas.drawLine(x, y - radius, x, y + radius, linePaint)
    }

    private fun drawRing(
        canvas: Canvas,
        center: Vector2D,
        boardMin: Float,
        logicalRadius: Float,
        color: Int,
        widthDp: Float
    ) {
        val x = projection.x(center)
        val y = projection.y(center)
        val radius = radiusToPx(logicalRadius, boardMin)
        fillPaint.color = withAlpha(color, 28)
        canvas.drawCircle(x, y, radius, fillPaint)
        linePaint.color = color
        linePaint.strokeWidth = widthDp * density
        linePaint.pathEffect = null
        canvas.drawCircle(x, y, radius, linePaint)
    }

    private fun buildPath(points: List<Vector2D>) {
        path.reset()
        if (points.isEmpty()) return
        path.moveTo(projection.x(points.first()), projection.y(points.first()))
        for (index in 1 until points.size) {
            path.lineTo(projection.x(points[index]), projection.y(points[index]))
        }
    }

    private fun radiusToPx(logical: Float, boardMin: Float): Float =
        logical / logicalSize * boardMin

    private fun sameTarget(a: ShotTrajectory, b: ShotTrajectory): Boolean =
        a.targetCoinPos == b.targetCoinPos && a.pocketTarget == b.pocketTarget
}
