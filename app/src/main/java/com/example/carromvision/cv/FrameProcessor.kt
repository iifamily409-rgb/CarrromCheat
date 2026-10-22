package com.example.carromvision.cv

import android.graphics.PixelFormat
import android.graphics.RectF
import android.media.Image
import android.os.SystemClock
import android.util.Log
import com.example.carromvision.domain.BoardSpec
import com.example.carromvision.domain.CoinEntity
import com.example.carromvision.domain.PhysicsSolver
import com.example.carromvision.domain.Vector2D
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Rect
import org.opencv.imgproc.Imgproc
import kotlin.math.roundToInt

class FrameProcessor(
    val logicalBoardSize: Float = PhysicsSolver.DEFAULT_BOARD_SIZE
) {
    @Volatile
    private var manualBoardRect: RectF? = null
    private val frameMapper = RgbaFrameMapper()
    private val boardLocator = BoardLocator()
    private val colorDetector = ColorBlobDetector()
    private val arrowDetector = ArrowDetector()
    private val physicsSolver = PhysicsSolver(logicalBoardSize, BoardSpec.POCKET_INSET)

    private var bgrBuffer: Mat? = null
    private var hsvBuffer: Mat? = null
    private var roiRgba: Mat? = null
    private var roiBounds: Rect? = null
    private var lastVisibleRgba: Mat? = null
    private var frameWidth = 0
    private var frameHeight = 0
    private var lastLogAtMs = 0L

    fun setManualBoardRect(rect: RectF?) {
        manualBoardRect = rect?.let(::RectF)
    }

    fun detect(image: Image): FrameDetection? {
        if (image.format != PixelFormat.RGBA_8888 &&
            image.format != android.graphics.ImageFormat.FLEX_RGBA_8888
        ) return null
        if (image.width != frameWidth || image.height != frameHeight) {
            releaseBuffers()
            frameWidth = image.width
            frameHeight = image.height
        }

        val rgba = frameMapper.map(image) ?: return null
        if (rgba !== lastVisibleRgba) {
            roiRgba?.release()
            roiRgba = null
            roiBounds = null
            lastVisibleRgba = rgba
        }

        val board = boardLocator.locate(
            rgba, SystemClock.elapsedRealtime(), manualBoardRect
        )
        if (!MatchGate.validBoard(board, image.width, image.height)) {
            return inactive(board, image, "board bounds")
        }
        val roi = toRoi(board, image.width, image.height) ?: return null
        val roiMat = obtainRoi(rgba, roi)
        val bgr = obtainColorBuffer(bgrBuffer, roi.width, roi.height)
            .also { bgrBuffer = it }
        val hsv = obtainColorBuffer(hsvBuffer, roi.width, roi.height)
            .also { hsvBuffer = it }

        return try {
            Imgproc.cvtColor(roiMat, bgr, Imgproc.COLOR_RGBA2BGR)
            Imgproc.cvtColor(bgr, hsv, Imgproc.COLOR_BGR2HSV)

            val blobs = colorDetector.detect(hsv)
            val boardWidthPx = roi.width.toFloat()
            val coinCandidates = blobs.coins.filter {
                MatchGate.validCoinRadius(it.radius, boardWidthPx) &&
                    !MatchGate.isNearPocket(it.toLogical(roi))
            }
            val strikerCandidates = blobs.strikerCandidates.filter { blob ->
                val logical = blob.toLogical(roi)
                MatchGate.validStrikerRadius(blob.radius, boardWidthPx) &&
                    !MatchGate.isNearPocket(logical)
            }
            val strikerBlob = PieceSelector.selectStriker(
                strikerCandidates, roi.height, boardWidthPx
            ) ?: return inactive(board, image, "striker")

            val queenBlob = coinCandidates
                .filter { it.kind == BlobKind.RED }
                .filter {
                    it.center.distanceTo(strikerBlob.center) >
                        (it.radius + strikerBlob.radius) * 0.85f
                }
                .maxByOrNull { it.radius }
            val coins = coinCandidates
                .asSequence()
                .filter {
                    it.center.distanceTo(strikerBlob.center) >
                        (it.radius + strikerBlob.radius) * 0.85f
                }
                .take(MAX_PIECES)
                .mapIndexed { index, blob ->
                    CoinEntity(
                        id = index + 1,
                        position = blob.toLogical(roi),
                        radius = BoardSpec.COIN_RADIUS,
                        isQueen = blob === queenBlob
                    )
                }
                .toList()
            if (coins.isEmpty()) return inactive(board, image, "coins")

            val striker = CoinEntity(
                id = 0,
                position = strikerBlob.toLogical(roi),
                radius = BoardSpec.STRIKER_RADIUS
            )

            // State Machine: Idle vs Active Drag
            val arrow = arrowDetector.detect(
                hsv, strikerBlob.center, strikerBlob.radius
            )
            val isDragging = arrow.isDragging

            var primary: com.example.carromvision.domain.ShotTrajectory? = null
            var assists: List<com.example.carromvision.domain.ShotTrajectory> = emptyList()

            if (isDragging) {
                // ACTIVE DRAG: Lock onto aimVector.aimDir, cast ray from striker center
                val aimDir = arrow.aimDir ?: return inactive(board, image, "no aim dir")
                val power = arrow.power
                primary = physicsSolver.solveShot(striker, coins, aimDir, true, power)
                assists = emptyList() // Suppress ALL background assists
            } else {
                // IDLE STATE: Return null trajectory and empty assists - canvas stays 100% CLEAN
                primary = null
                assists = emptyList()
            }

            logThrottled(
                "active striker=${striker.position.x.toInt()},${striker.position.y.toInt()} " +
                    "coins=${coins.size} drag=$isDragging primary=${primary != null}"
            )
            FrameDetection(
                trajectory = primary,
                assists = assists,
                boardRect = RectF(board),
                frameWidth = image.width,
                frameHeight = image.height,
                isMatchActive = true
            )
        } catch (error: Exception) {
            Log.e(TAG, "Frame pipeline error", error)
            null
        }
    }

    fun close() = releaseBuffers()

    private fun inactive(board: RectF, image: Image, reason: String): FrameDetection {
        if (reason != lastInactiveReason) {
            lastInactiveReason = reason
            logThrottled("match inactive: $reason")
        }
        return FrameDetection(null, emptyList(), RectF(board), image.width, image.height, false)
    }

    private var lastInactiveReason: String? = null

    private fun logThrottled(message: String) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastLogAtMs >= LOG_INTERVAL_MS) {
            lastLogAtMs = now
            Log.d(TAG, message)
        }
    }

    private fun obtainRoi(rgba: Mat, roi: Rect): Mat {
        val current = roiRgba
        val bounds = roiBounds
        if (current != null && bounds != null &&
            bounds.x == roi.x && bounds.y == roi.y &&
            bounds.width == roi.width && bounds.height == roi.height
        ) return current
        current?.release()
        roiBounds = Rect(roi.x, roi.y, roi.width, roi.height)
        return rgba.submat(roi).also { roiRgba = it }
    }

    private fun obtainColorBuffer(current: Mat?, width: Int, height: Int): Mat {
        if (current != null && current.rows() == height &&
            current.cols() == width && current.type() == CvType.CV_8UC3
        ) return current
        current?.release()
        return Mat(height, width, CvType.CV_8UC3)
    }

    private fun releaseBuffers() {
        roiRgba?.release()
        bgrBuffer?.release()
        hsvBuffer?.release()
        roiRgba = null
        bgrBuffer = null
        hsvBuffer = null
        roiBounds = null
        lastVisibleRgba = null
        frameWidth = 0
        frameHeight = 0
        colorDetector.close()
        arrowDetector.close()
        frameMapper.release()
    }

    private fun toRoi(board: RectF, frameWidth: Int, frameHeight: Int): Rect? {
        val left = (board.left * frameWidth).roundToInt().coerceIn(0, frameWidth - 1)
        val top = (board.top * frameHeight).roundToInt().coerceIn(0, frameHeight - 1)
        val right = (board.right * frameWidth).roundToInt()
            .coerceIn(left + 1, frameWidth)
        val bottom = (board.bottom * frameHeight).roundToInt()
            .coerceIn(top + 1, frameHeight)
        if (right - left < MIN_ROI_SIZE || bottom - top < MIN_ROI_SIZE) return null
        return Rect(left, top, right - left, bottom - top)
    }

    private fun ColorBlob.toLogical(roi: Rect): Vector2D = Vector2D(
        (center.x / roi.width * logicalBoardSize).toFloat(),
        (center.y / roi.height * logicalBoardSize).toFloat()
    )

    private companion object {
        const val TAG = "FP_DEBUG"
        const val MIN_ROI_SIZE = 64
        const val MAX_PIECES = 20
        const val FALLBACK_POWER = 0.78f
        const val LOG_INTERVAL_MS = 1_200L
    }
}
