package com.example.carromvision.cv

import android.graphics.RectF
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Rect
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.max
import kotlin.math.min

/**
 * Locates the carrom board as the largest convex quadrilateral in the frame.
 * Falls back to a precise centered square board ROI if quadrilateral detection fails.
 */
internal class BoardLocator {
    private var cachedRect = RectF(0f, 0f, 1f, 1f)
    private var lastScanAtMs = 0L

    fun locate(rgba: Mat, nowMs: Long, manualRect: RectF?): RectF {
        manualRect?.let { return RectF(it) }
        if (nowMs - lastScanAtMs < SCAN_INTERVAL_MS) return RectF(cachedRect)

        lastScanAtMs = nowMs
        cachedRect = findBoardQuad(rgba) ?: fallbackCenterBoard(rgba)
        return RectF(cachedRect)
    }

    private fun findBoardQuad(rgba: Mat): RectF? {
        val scale = min(1f, SCAN_MAX_DIM / max(rgba.cols(), rgba.rows()).toFloat())
        val small = Mat()
        val gray = Mat()
        val edges = Mat()
        val contours = ArrayList<MatOfPoint>()
        val hierarchy = Mat()

        try {
            Imgproc.resize(
                rgba, small,
                Size(rgba.cols() * scale.toDouble(), rgba.rows() * scale.toDouble()),
                0.0, 0.0, Imgproc.INTER_AREA
            )
            Imgproc.cvtColor(small, gray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.GaussianBlur(gray, gray, Size(5.0, 5.0), 0.0)
            Imgproc.Canny(gray, edges, 60.0, 180.0)
            Imgproc.findContours(
                edges, contours, hierarchy,
                Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE
            )

            val minArea = edges.total() * MIN_BOARD_AREA_FRACTION
            var best: Rect? = null
            var bestArea = 0.0

            for (contour in contours) {
                val area = Imgproc.contourArea(contour)
                if (area < minArea) continue

                val polygon = MatOfPoint2f(*contour.toArray())
                val approx = MatOfPoint2f()
                try {
                    val perimeter = Imgproc.arcLength(polygon, true)
                    Imgproc.approxPolyDP(polygon, approx, perimeter * 0.025, true)
                    if (approx.total() != 4L) continue

                    val points = MatOfPoint(*approx.toArray())
                    try {
                        if (!Imgproc.isContourConvex(points)) continue
                        val rect = Imgproc.boundingRect(points)
                        if (rect.height <= 0) continue

                        val aspect = rect.width.toFloat() / rect.height
                        if (aspect !in MIN_ASPECT..MAX_ASPECT || area <= bestArea) continue
                        bestArea = area
                        best = rect
                    } finally {
                        points.release()
                    }
                } finally {
                    polygon.release()
                    approx.release()
                }
            }

            val rect = best ?: return null
            val width = small.cols().toFloat()
            val height = small.rows().toFloat()

            // The physics surface is a 1:1 logical square. Expand the detected
            // quad along its shorter axis so the normalized ROI keeps the same
            // scale on X and Y, then slide the square back inside the frame.
            val side = maxOf(rect.width.toFloat(), rect.height.toFloat())
                .coerceAtMost(minOf(width, height))
            if (side < MIN_SQUARE_PX) return null

            val centerX = rect.x + rect.width / 2f
            val centerY = rect.y + rect.height / 2f
            val left = (centerX - side / 2f).coerceIn(0f, width - side)
            val top = (centerY - side / 2f).coerceIn(0f, height - side)
            val right = left + side
            val bottom = top + side
            return RectF(
                left / width,
                top / height,
                right / width,
                bottom / height
            )
        } finally {
            for (contour in contours) contour.release()
            hierarchy.release()
            small.release()
            gray.release()
            edges.release()
        }
    }

    private fun fallbackCenterBoard(rgba: Mat): RectF {
        val screenW = rgba.cols().toFloat()
        val screenH = rgba.rows().toFloat()
        // A carrom board is square: fit it to the shorter display dimension so
        // the fallback remains correct in both portrait and landscape.
        val boardSize = minOf(screenW, screenH) * 0.94f
        val left = (screenW - boardSize) / 2f
        val top = (screenH - boardSize) / 2f
        val right = left + boardSize
        val bottom = top + boardSize
        return RectF(
            (left / screenW).coerceIn(0f, 1f),
            (top / screenH).coerceIn(0f, 1f),
            (right / screenW).coerceIn(0f, 1f),
            (bottom / screenH).coerceIn(0f, 1f)
        )
    }

    private companion object {
        const val SCAN_INTERVAL_MS = 2_000L
        const val SCAN_MAX_DIM = 480f
        const val MIN_BOARD_AREA_FRACTION = 0.10
        const val MIN_SQUARE_PX = 64f
        const val MIN_ASPECT = 0.55f
        const val MAX_ASPECT = 1.8f
    }
}
