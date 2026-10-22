package com.example.carromvision.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.RectF
import android.view.View
import com.example.carromvision.domain.ShotTrajectory

/**
 * Transparent, hardware-accelerated canvas for the primary aim line, multi-puck
 * assist rays and cushion-bank rebound markers.
 */
class AimOverlayView(context: Context) : View(context) {
    @Volatile private var primary: ShotTrajectory? = null
    @Volatile private var assists: List<ShotTrajectory> = emptyList()
    @Volatile private var viewport = BoardViewport()
    private val painter = TrajectoryPainter(resources.displayMetrics.density)

    var config: OverlayConfig
        get() = painter.config
        set(value) {
            painter.config = value
            postInvalidateOnAnimation()
        }

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    fun update(
        shot: ShotTrajectory?,
        multiShots: List<ShotTrajectory>,
        boardRect: RectF,
        logicalSize: Float
    ) {
        primary = shot
        assists = multiShots
        viewport = BoardViewport(
            left = boardRect.left,
            top = boardRect.top,
            right = boardRect.right,
            bottom = boardRect.bottom,
            logicalSize = logicalSize.coerceAtLeast(1f)
        )
        postInvalidateOnAnimation()
    }

    /** Drops all geometry and repaints immediately, leaving a blank layer. */
    fun clear() {
        if (primary == null && assists.isEmpty()) return
        primary = null
        assists = emptyList()
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        val shot = primary ?: return
        if (width <= 0 || height <= 0) return
        painter.draw(canvas, width, height, viewport, shot, assists)
    }

    override fun onDetachedFromWindow() {
        primary = null
        assists = emptyList()
        super.onDetachedFromWindow()
    }
}
