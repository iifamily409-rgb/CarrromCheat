package com.example.carromvision.cv

import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sqrt

internal enum class BlobKind { WHITE, BLACK, RED, STRIKER_CANDIDATE }

internal data class ColorBlob(
    val center: Point,
    val radius: Float,
    val kind: BlobKind
)

internal data class ColorBlobs(
    val coins: List<ColorBlob>,
    val strikerCandidates: List<ColorBlob>
)

internal fun Point.distanceTo(other: Point): Float =
    hypot((x - other.x).toFloat(), (y - other.y).toFloat())

/**
 * HSV dual-band segmentation + universal circular blob detection for carrom pieces,
 * supporting custom-skinned strikers (skins, textures, multi-color).
 */
internal class ColorBlobDetector {
    private val white = Mat()
    private val black = Mat()
    private val redLow = Mat()
    private val redHigh = Mat()
    private val red = Mat()
    private val saturated = Mat()
    private val universalMask = Mat()
    private val vChannel = Mat()
    private val masks = listOf(
        white, black, redLow, redHigh, red, saturated, universalMask, vChannel
    )

    private var kernel: Mat? = null
    private var maskRows = -1
    private var maskCols = -1

    fun detect(hsv: Mat): ColorBlobs {
        ensureMasks(hsv.rows(), hsv.cols())
        val structuringElement = ensureKernel()

        Core.inRange(hsv, Scalar(0.0, 0.0, 140.0), Scalar(180.0, 72.0, 255.0), white)
        Core.inRange(hsv, Scalar(0.0, 0.0, 0.0), Scalar(180.0, 255.0, 82.0), black)
        Core.inRange(hsv, Scalar(0.0, 105.0, 80.0), Scalar(10.0, 255.0, 255.0), redLow)
        Core.inRange(hsv, Scalar(170.0, 105.0, 80.0), Scalar(180.0, 255.0, 255.0), redHigh)
        // The queen wraps around the 0/180 hue boundary. Merge both bands,
        // otherwise the `red` mask passed to collectBlobs() is always empty.
        Core.bitwise_or(redLow, redHigh, red)
        Core.inRange(hsv, Scalar(0.0, 90.0, 80.0), Scalar(180.0, 255.0, 255.0), saturated)

        Core.extractChannel(hsv, vChannel, 2)
        Imgproc.adaptiveThreshold(
            vChannel, universalMask, 255.0,
            Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY_INV, 21, 5.0
        )

        for (mask in listOf(white, black, red, saturated, universalMask)) {
            Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_OPEN, structuringElement)
            Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_CLOSE, structuringElement)
        }

        // findContours may alter its source mask, so the white band is scanned
        // once and shared by both the coin and striker candidate collections.
        val whiteBlobs = ArrayList<ColorBlob>(24)
        collectBlobs(white, BlobKind.WHITE, whiteBlobs)

        val coinBlobs = ArrayList<ColorBlob>(24)
        coinBlobs.addAll(whiteBlobs)
        collectBlobs(black, BlobKind.BLACK, coinBlobs)
        collectBlobs(red, BlobKind.RED, coinBlobs)

        val strikerBlobs = ArrayList<ColorBlob>(16)
        strikerBlobs.addAll(whiteBlobs)
        collectBlobs(universalMask, BlobKind.STRIKER_CANDIDATE, strikerBlobs)
        collectBlobs(saturated, BlobKind.WHITE, strikerBlobs)

        return ColorBlobs(
            coins = deduplicate(coinBlobs),
            strikerCandidates = deduplicate(strikerBlobs)
        )
    }

    fun close() {
        masks.forEach { it.release() }
        kernel?.release()
        kernel = null
        maskRows = -1
        maskCols = -1
    }

    private fun ensureMasks(rows: Int, cols: Int) {
        ensureKernel()
        if (rows == maskRows && cols == maskCols &&
            white.type() == CvType.CV_8UC1
        ) return

        masks.forEach { it.create(rows, cols, CvType.CV_8UC1) }
        maskRows = rows
        maskCols = cols
    }

    private fun ensureKernel(): Mat =
        kernel ?: createKernel().also { kernel = it }

    private fun collectBlobs(mask: Mat, kind: BlobKind, output: MutableList<ColorBlob>) {
        val contours = ArrayList<MatOfPoint>(24)
        val hierarchy = Mat()
        try {
            Imgproc.findContours(
                mask, contours, hierarchy,
                Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE
            )
            val maskArea = mask.cols().toDouble() * mask.rows()

            for (contour in contours) {
                val area = Imgproc.contourArea(contour)
                if (area < maskArea * MIN_AREA_FRACTION ||
                    area > maskArea * MAX_AREA_FRACTION
                ) continue

                val contour2f = MatOfPoint2f(*contour.toArray())
                val perimeter = try {
                    Imgproc.arcLength(contour2f, true)
                } finally {
                    contour2f.release()
                }
                if (perimeter <= 0.0) continue

                val circularity = 4.0 * PI * area / (perimeter * perimeter)
                if (circularity < MIN_CIRCULARITY) continue

                val moments = Imgproc.moments(contour)
                if (moments.m00 <= 0.0) continue

                output.add(
                    ColorBlob(
                        center = Point(moments.m10 / moments.m00, moments.m01 / moments.m00),
                        radius = sqrt(area / PI).toFloat(),
                        kind = kind
                    )
                )
            }
        } finally {
            for (contour in contours) contour.release()
            hierarchy.release()
        }
    }

    private fun deduplicate(blobs: List<ColorBlob>): List<ColorBlob> {
        val result = ArrayList<ColorBlob>(blobs.size)
        for (blob in blobs.sortedByDescending { it.radius }) {
            val duplicate = result.any {
                it.center.distanceTo(blob.center) < max(it.radius, blob.radius) * 0.65f
            }
            if (!duplicate) result.add(blob)
        }
        return result
    }

    private fun createKernel(): Mat =
        Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(3.0, 3.0))

    private companion object {
        const val MIN_AREA_FRACTION = 0.0003
        const val MAX_AREA_FRACTION = 0.02
        const val MIN_CIRCULARITY = 0.50
    }
}
