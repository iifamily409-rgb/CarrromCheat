package com.example.carromvision.cv

import android.graphics.RectF
import com.example.carromvision.domain.BoardSpec
import com.example.carromvision.domain.Vector2D
import org.opencv.core.Mat

/** Lightweight match-state gate used before any trajectory is published.
 *  - Pocket detection is now completely optional (fallback to board corners).
 *  - Striker placement across the playable area without rigid baseline restrictions.
 *  - If a square board ROI is detected, ALWAYS mark isMatchActive = true. */
internal object MatchGate {
    /**
     * Validates the board in pixel space. A normalized rectangle is anisotropic
     * on any non-square display, so width/height must be corrected by the frame
     * dimensions before the 1:1 gate is applied.
     */
    fun validBoard(rect: RectF, frameWidth: Int, frameHeight: Int): Boolean {
        val width = rect.width()
        val height = rect.height()
        if (frameWidth <= 0 || frameHeight <= 0) return false
        if (rect.left < -0.03f || rect.top < -0.03f ||
            rect.right > 1.03f || rect.bottom > 1.03f
        ) return false

        val pixelWidth = width * frameWidth
        val pixelHeight = height * frameHeight
        val minSide = minOf(frameWidth, frameHeight) * MIN_BOARD_SIZE
        if (pixelWidth < minSide || pixelHeight < minSide) return false

        val pixelAspect = pixelWidth / pixelHeight
        return pixelAspect in MIN_ASPECT..MAX_ASPECT
    }

    /** Rect-only overload for already square-normalized callers and tests. */
    fun validBoard(rect: RectF): Boolean = validBoard(rect, 1, 1)

    /**
     * Pocket detection is now OPTIONAL. Returns true to allow fallback to
     * board-corner pockets. Kept for compatibility but never gates the match.
     */
    fun cornerPocketsPresent(hsvRoi: Mat): Boolean = true

    /**
     * Baseline restriction removed. Striker can be anywhere in the playable area.
     * Always returns true to allow flexible striker positioning.
     */
    fun onBaseline(centerY: Float, roiHeight: Float): Boolean = true

    fun isNearPocket(position: Vector2D): Boolean {
        val inset = BoardSpec.POCKET_INSET
        val clearance = BoardSpec.POCKET_RADIUS * POCKET_CLEARANCE_SCALE
        return position.distanceTo(Vector2D(inset, inset)) < clearance ||
            position.distanceTo(Vector2D(BoardSpec.LOGICAL_SIZE - inset, inset)) < clearance ||
            position.distanceTo(Vector2D(inset, BoardSpec.LOGICAL_SIZE - inset)) < clearance ||
            position.distanceTo(
                Vector2D(BoardSpec.LOGICAL_SIZE - inset, BoardSpec.LOGICAL_SIZE - inset)
            ) < clearance
    }

    fun validCoinRadius(radiusPx: Float, boardWidthPx: Float): Boolean {
        val expected = expectedRadius(BoardSpec.COIN_RADIUS, boardWidthPx)
        return radiusPx in (expected * MIN_RADIUS_SCALE)..(expected * MAX_RADIUS_SCALE)
    }

    fun validStrikerRadius(radiusPx: Float, boardWidthPx: Float): Boolean {
        val expected = expectedRadius(BoardSpec.STRIKER_RADIUS, boardWidthPx)
        return radiusPx in (expected * MIN_RADIUS_SCALE)..(expected * MAX_RADIUS_SCALE)
    }

    private fun expectedRadius(logicalRadius: Float, boardWidthPx: Float): Float =
        boardWidthPx * (logicalRadius / BoardSpec.LOGICAL_SIZE)

    private const val MIN_BOARD_SIZE = 0.30f
    private const val MIN_ASPECT = 0.88f
    private const val MAX_ASPECT = 1.14f
    private const val MIN_RADIUS_SCALE = 0.55f
    private const val MAX_RADIUS_SCALE = 1.55f
    private const val POCKET_CLEARANCE_SCALE = 1.35f
}
