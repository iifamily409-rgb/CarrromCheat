package com.example.carromvision.cv

import android.media.Image
import org.opencv.core.CvType
import org.opencv.core.Mat
import java.util.Arrays
import kotlin.math.max
import kotlin.math.min

/**
 * Reusable mapping of a MediaProjection RGBA_8888 [Image] into a CV_8UC4 [Mat],
 * preserving row-stride padding.
 *
 * The returned Mat is borrowed: it stays valid until the next [map] call or
 * [release]. Both the byte buffer and native Mats are reused across frames to
 * avoid native allocator churn. Call only from the vision worker thread.
 */
internal class RgbaFrameMapper {
    private var buffer = ByteArray(0)
    private var padded: Mat? = null
    private var visible: Mat? = null

    fun map(image: Image): Mat? {
        if (image.planes.isEmpty()) return null
        val plane = image.planes[0]
        if (plane.pixelStride != BYTES_PER_PIXEL) return null

        val width = image.width
        val height = image.height
        val rowStride = max(plane.rowStride, width * BYTES_PER_PIXEL)
        val paddedCols = max(width, rowStride / BYTES_PER_PIXEL)
        val required = paddedCols * BYTES_PER_PIXEL * height

        // Reallocate before copying: release() clears the byte buffer as well.
        var target = padded
        if (target == null || target.rows() != height || target.cols() != paddedCols) {
            release()
            target = Mat(height, paddedCols, CvType.CV_8UC4)
            padded = target
        }

        if (buffer.size < required) buffer = ByteArray(required)
        val planeBuffer = plane.buffer
        planeBuffer.rewind()
        val copied = min(required, planeBuffer.remaining())
        planeBuffer.get(buffer, 0, copied)
        if (copied < required) Arrays.fill(buffer, copied, required, 0)
        target.put(0, 0, buffer, 0, required)

        if (paddedCols == width) {
            visible = target
            return target
        }

        var cropped = visible
        if (cropped == null || cropped.rows() != height || cropped.cols() != width) {
            cropped?.release()
            cropped = target.submat(0, height, 0, width)
            visible = cropped
        }
        return cropped
    }

    fun release() {
        val cropped = visible
        if (cropped !== padded) cropped?.release()
        padded?.release()
        visible = null
        padded = null
        buffer = ByteArray(0)
    }

    private companion object {
        const val BYTES_PER_PIXEL = 4
    }
}
