package com.example.carromvision.cv

import android.util.Log
import com.example.carromvision.domain.Vector2D
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.Point
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.hypot

data class AimVectorResult(
    val aimDir: Vector2D?,
    val power: Float,
    val isDragging: Boolean
)

/**
 * Finds the orange/yellow/red in-game drag arrow around the striker.
 *
 * Power is the raw normalized drag distance. [com.example.carromvision.domain
 * .CollisionPhysics.velocityForPower] applies the t^1.4 easing curve.
 *
 * HSV ranges expanded:
 *   - Orange:  Hue [10..35], Sat [120..255], Val [150..255]
 *   - Yellow:  Hue [20..40], Sat [100..255], Val [180..255]
 *   - Red:     Hue [0..10] U [170..180], Sat [100..255], Val [150..255]
 *
 * If direct contour detection fails, falls back to striker touch displacement
 * relative to its resting baseline position.
 */
internal class ArrowDetector {
    // Primary orange/yellow range
    private val lowerPrimary = Scalar(10.0, 120.0, 150.0)
    private val upperPrimary = Scalar(35.0, 255.0, 255.0)

    // Extended yellow range
    private val lowerYellow = Scalar(20.0, 100.0, 180.0)
    private val upperYellow = Scalar(40.0, 255.0, 255.0)

    // Red range (wraps around 0/180 in OpenCV HSV)
    private val lowerRed1 = Scalar(0.0, 100.0, 150.0)
    private val upperRed1 = Scalar(10.0, 255.0, 255.0)
    private val lowerRed2 = Scalar(170.0, 100.0, 150.0)
    private val upperRed2 = Scalar(180.0, 255.0, 255.0)

    private var morphKernel: Mat? = null
    private var lastStrikerCenter: Point? = null
    private var baselineStrikerCenter: Point? = null
    private var baselineEstablished = false

    private fun kernel(): Mat = morphKernel ?: Imgproc.getStructuringElement(
        Imgproc.MORPH_ELLIPSE, Size(5.0, 5.0)
    ).also { morphKernel = it }

    fun detect(
        hsvRoi: Mat,
        strikerCenterPx: Point,
        strikerRadiusPx: Float
    ): AimVectorResult {
        if (hsvRoi.empty() || strikerRadiusPx <= 0f) {
            return AimVectorResult(null, 0f, false)
        }

        // Track striker position for fallback displacement detection
        updateBaseline(strikerCenterPx, strikerRadiusPx)

        val searchRadius = (strikerRadiusPx * SEARCH_RADIUS_SCALE).toInt()
        val roiX = (strikerCenterPx.x - searchRadius).toInt()
            .coerceIn(0, hsvRoi.cols() - 1)
        val roiY = (strikerCenterPx.y - searchRadius).toInt()
            .coerceIn(0, hsvRoi.rows() - 1)
        val roiW = (searchRadius * 2).coerceAtMost(hsvRoi.cols() - roiX)
        val roiH = (searchRadius * 2).coerceAtMost(hsvRoi.rows() - roiY)
        if (roiW <= 10 || roiH <= 10) return AimVectorResult(null, 0f, false)

        var subHsv: Mat? = null
        var mask: Mat? = null
        var hierarchy: Mat? = null
        val contours = ArrayList<MatOfPoint>()

        try {
            val region = hsvRoi.submat(Rect(roiX, roiY, roiW, roiH))
            subHsv = region
            val workingMask = Mat(region.size(), CvType.CV_8UC1)
            mask = workingMask
            val workingHierarchy = Mat()
            hierarchy = workingHierarchy

            // Combine multiple HSV ranges for robust arrow detection
            val maskPrimary = Mat(region.size(), CvType.CV_8UC1)
            val maskYellow = Mat(region.size(), CvType.CV_8UC1)
            val maskRed1 = Mat(region.size(), CvType.CV_8UC1)
            val maskRed2 = Mat(region.size(), CvType.CV_8UC1)

            Core.inRange(region, lowerPrimary, upperPrimary, maskPrimary)
            Core.inRange(region, lowerYellow, upperYellow, maskYellow)
            Core.inRange(region, lowerRed1, upperRed1, maskRed1)
            Core.inRange(region, lowerRed2, upperRed2, maskRed2)

            Core.bitwise_or(maskPrimary, maskYellow, workingMask)
            Core.bitwise_or(workingMask, maskRed1, workingMask)
            Core.bitwise_or(workingMask, maskRed2, workingMask)

            maskPrimary.release()
            maskYellow.release()
            maskRed1.release()
            maskRed2.release()

            Imgproc.morphologyEx(workingMask, workingMask, Imgproc.MORPH_CLOSE, kernel())
            Imgproc.findContours(
                workingMask, contours, workingHierarchy,
                Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE
            )

            var bestPoints: Array<Point>? = null
            var bestArea = 0.0
            for (contour in contours) {
                val area = Imgproc.contourArea(contour)
                if (area > bestArea && area > MIN_AREA) {
                    bestArea = area
                    bestPoints = contour.toArray()
                }
            }

            val points = bestPoints ?: return fallbackFromDisplacement(strikerCenterPx, strikerRadiusPx)
            val localStriker = Point(
                strikerCenterPx.x - roiX,
                strikerCenterPx.y - roiY
            )
            var farthest = points[0]
            var maxDistance = 0f
            for (point in points) {
                val distance = hypot(
                    (point.x - localStriker.x).toFloat(),
                    (point.y - localStriker.y).toFloat()
                )
                if (distance > maxDistance) {
                    maxDistance = distance
                    farthest = point
                }
            }
            if (maxDistance <= strikerRadiusPx * TIP_MIN_RADIUS_SCALE) {
                return fallbackFromDisplacement(strikerCenterPx, strikerRadiusPx)
            }

            val tipX = (farthest.x + roiX).toFloat()
            val tipY = (farthest.y + roiY).toFloat()
            val direction = Vector2D(
                (tipX - strikerCenterPx.x).toFloat(),
                (tipY - strikerCenterPx.y).toFloat()
            ).normalized()
            if (direction.isZero()) return fallbackFromDisplacement(strikerCenterPx, strikerRadiusPx)

            val deadZone = strikerRadiusPx * DRAG_DEAD_ZONE_SCALE
            val maxDrag = strikerRadiusPx * MAX_DRAG_SCALE
            val power = ((maxDistance.toFloat() - deadZone) / (maxDrag - deadZone))
                .coerceIn(0f, 1f)
            return AimVectorResult(direction, power, true)
        } catch (error: Exception) {
            Log.e(TAG, "Arrow detection failed", error)
            return fallbackFromDisplacement(strikerCenterPx, strikerRadiusPx)
        } finally {
            for (contour in contours) contour.release()
            hierarchy?.release()
            mask?.release()
            subHsv?.release()
        }
    }

    /**
     * Fallback: compute aim vector from striker displacement relative to
     * established baseline position (touch-drag vector).
     */
    private fun fallbackFromDisplacement(
        strikerCenterPx: Point,
        strikerRadiusPx: Float
    ): AimVectorResult {
        val baseline = baselineStrikerCenter ?: return AimVectorResult(null, 0f, false)
        val dx = (strikerCenterPx.x - baseline.x).toFloat()
        val dy = (strikerCenterPx.y - baseline.y).toFloat()
        val dist = hypot(dx, dy)

        val deadZone = strikerRadiusPx * DRAG_DEAD_ZONE_SCALE
        if (dist <= deadZone) {
            // Not dragging significantly - might be idle
            return AimVectorResult(null, 0f, false)
        }

        val maxDrag = strikerRadiusPx * MAX_DRAG_SCALE
        val power = ((dist - deadZone) / (maxDrag - deadZone)).coerceIn(0f, 1f)
        val direction = Vector2D(dx, dy).normalized()
        if (direction.isZero()) return AimVectorResult(null, 0f, false)

        return AimVectorResult(direction, power, true)
    }

    /**
     * Tracks striker position to establish a resting baseline for
     * displacement-based fallback detection.
     */
    private fun updateBaseline(strikerCenterPx: Point, strikerRadiusPx: Float) {
        if (!baselineEstablished) {
            baselineStrikerCenter = Point(strikerCenterPx.x, strikerCenterPx.y)
            baselineEstablished = true
        } else {
            // If striker moves significantly, it's being dragged - update last known
            val dx = strikerCenterPx.x - (baselineStrikerCenter?.x ?: strikerCenterPx.x)
            val dy = strikerCenterPx.y - (baselineStrikerCenter?.y ?: strikerCenterPx.y)
            val dist = hypot(dx.toFloat(), dy.toFloat())
            if (dist > strikerRadiusPx * 0.5f) {
                // Significant movement - likely dragging
                lastStrikerCenter = Point(strikerCenterPx.x, strikerCenterPx.y)
            } else if (dist < strikerRadiusPx * 0.1f && lastStrikerCenter != null) {
                // Returned to rest - update baseline
                baselineStrikerCenter = Point(strikerCenterPx.x, strikerCenterPx.y)
            }
        }
    }

    fun close() {
        morphKernel?.release()
        morphKernel = null
    }

    private companion object {
        const val TAG = "ArrowDetector"
        const val SEARCH_RADIUS_SCALE = 10f
        const val TIP_MIN_RADIUS_SCALE = 0.7f
        const val DRAG_DEAD_ZONE_SCALE = 0.4f
        const val MAX_DRAG_SCALE = 10f
        const val MIN_AREA = 10.0
    }
}
