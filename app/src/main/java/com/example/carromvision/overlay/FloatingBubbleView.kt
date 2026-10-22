package com.example.carromvision.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.SweepGradient
import android.view.View
import kotlin.math.min

/** Dark-rimmed metallic floating control badge with a cyan centre reticle. */
class FloatingBubbleView(context: Context) : View(context) {
    private val density = resources.displayMetrics.density
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val reticlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private var radialShader: Shader? = null
    private var sweepShader: Shader? = null

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        val cx = width / 2f
        val cy = height / 2f
        val radius = min(width, height) / 2f - dp(1f)
        if (radius <= 1f) return
        radialShader = RadialGradient(
            cx - radius * 0.24f, cy - radius * 0.30f, radius * 1.45f,
            intArrayOf(Color.rgb(52, 59, 72), Color.rgb(9, 11, 16)),
            floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
        )
        sweepShader = SweepGradient(
            cx, cy,
            intArrayOf(
                Color.rgb(55, 60, 68),
                Color.rgb(205, 216, 230),
                Color.rgb(245, 250, 255),
                Color.rgb(115, 124, 137),
                Color.rgb(55, 60, 68)
            ),
            null
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = min(width, height) / 2f - dp(1f)
        if (radius <= 1f) return

        fillPaint.color = Color.argb(185, 0, 0, 0)
        canvas.drawCircle(cx, cy, radius, fillPaint)

        radialShader?.let { shader ->
            fillPaint.shader = shader
            canvas.drawCircle(cx, cy, radius - dp(5f), fillPaint)
            fillPaint.shader = null
        }

        sweepShader?.let { shader ->
            ringPaint.shader = shader
            ringPaint.strokeWidth = dp(2.6f)
            canvas.drawCircle(cx, cy, radius - dp(4.5f), ringPaint)
            ringPaint.shader = null
        }

        reticlePaint.color = Color.argb(220, 70, 220, 255)
        reticlePaint.strokeWidth = dp(1.3f)
        canvas.drawCircle(cx, cy, dp(7.2f), reticlePaint)
        val inner = dp(3.2f)
        val outer = dp(11.5f)
        canvas.drawLine(cx - outer, cy, cx - inner, cy, reticlePaint)
        canvas.drawLine(cx + inner, cy, cx + outer, cy, reticlePaint)
        canvas.drawLine(cx, cy - outer, cx, cy - inner, reticlePaint)
        canvas.drawLine(cx, cy + inner, cx, cy + outer, reticlePaint)
        fillPaint.color = Color.WHITE
        canvas.drawCircle(cx, cy, dp(1.7f), fillPaint)
    }

    private fun dp(value: Float): Float = value * density
}
