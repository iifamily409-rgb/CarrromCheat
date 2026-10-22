package com.example.carromvision.cv

import com.example.carromvision.domain.BoardSpec
import com.example.carromvision.domain.CoinEntity
import com.example.carromvision.domain.Vector2D
import kotlin.math.abs
import kotlin.math.min

/** Striker candidate scoring and fallback aim helpers. */
internal object PieceSelector {
    fun selectStriker(
        candidates: List<ColorBlob>,
        roiHeight: Int,
        boardWidthPx: Float
    ): ColorBlob? {
        if (candidates.isEmpty()) return null
        val expectedRadius = boardWidthPx *
            (BoardSpec.STRIKER_RADIUS / BoardSpec.LOGICAL_SIZE)
        val baselineBand = roiHeight * BASELINE_SCORE_BAND
        return candidates.maxByOrNull { blob ->
            val nearestBaseline = min(blob.center.y, roiHeight - blob.center.y).toFloat()
            val baselineScore = (1f - nearestBaseline / baselineBand).coerceIn(0f, 1f)
            val sizeScore = (
                1f - abs(blob.radius - expectedRadius) / expectedRadius
            ).coerceIn(0f, 1f)
            baselineScore * 2f + sizeScore
        }
    }

    fun nearestDirection(
        striker: CoinEntity,
        coins: List<CoinEntity>
    ): Vector2D? {
        val nearest = coins.minByOrNull {
            it.position.distanceTo(striker.position)
        } ?: return null
        return (nearest.position - striker.position).normalized().takeIf { !it.isZero() }
    }

    private const val BASELINE_SCORE_BAND = 0.30f
}
