package com.example.carromvision.domain

import kotlin.math.abs

/**
 * Pure-Kotlin one/two cushion path solver using the mirror-image method.
 *
 * The target point is reflected across each cushion in reverse order. A straight
 * line to the final virtual target is intersected with the first cushion; folding
 * the virtual target back produces each real bounce point. Every real segment is
 * validated against the playable bounds and the caller's collision predicate.
 */
internal class CushionSolver(
    private val minX: Float,
    private val minY: Float,
    private val maxX: Float,
    private val maxY: Float
) {
    private val singleBounceSequences: List<List<Rail>> =
        Rail.entries.map { listOf(it) }

    private val doubleBounceSequences: List<List<Rail>> = buildList {
        Rail.entries.forEach { first ->
            Rail.entries.forEach { second -> add(listOf(first, second)) }
        }
    }

    fun findPath(
        start: Vector2D,
        goal: Vector2D,
        radius: Float,
        isClear: (Vector2D, Vector2D) -> Boolean
    ): List<Vector2D>? =
        findBest(start, goal, radius, isClear, singleBounceSequences)
            ?: findBest(start, goal, radius, isClear, doubleBounceSequences)

    private fun findBest(
        start: Vector2D,
        goal: Vector2D,
        radius: Float,
        isClear: (Vector2D, Vector2D) -> Boolean,
        sequences: List<List<Rail>>
    ): List<Vector2D>? {
        var best: List<Vector2D>? = null
        var bestLength = Float.MAX_VALUE

        for (sequence in sequences) {
            val path = buildPath(start, goal, sequence, radius) ?: continue
            if (!isPathClear(path, isClear)) continue

            val length = polylineLength(path)
            if (length < bestLength) {
                bestLength = length
                best = path
            }
        }
        return best
    }

    private fun buildPath(
        start: Vector2D,
        goal: Vector2D,
        sequence: List<Rail>,
        radius: Float
    ): List<Vector2D>? {
        val images = MutableList(sequence.size) { Vector2D.ZERO }
        var virtualTarget = goal
        for (index in sequence.indices.reversed()) {
            virtualTarget = reflect(virtualTarget, sequence[index], radius)
            images[index] = virtualTarget
        }

        val path = ArrayList<Vector2D>(sequence.size + 2)
        path.add(start)

        var cursor = start
        for (index in sequence.indices) {
            val aimPoint = images[index]
            val t = intersection(cursor, aimPoint, sequence[index], radius) ?: return null
            // t is a unit-less fraction along cursor -> aimPoint, not a distance.
            if (t <= 0f || t > 1f) return null

            val bounce = cursor + (aimPoint - cursor) * t
            if (!onRail(bounce, sequence[index], radius)) return null
            if (bounce.distanceTo(cursor) <= MIN_TRAVEL) return null
            if (path.any { it.distanceTo(bounce) <= MIN_TRAVEL }) return null

            path.add(bounce)
            cursor = bounce
        }
        path.add(goal)

        for (index in 0 until path.size - 1) {
            if (!insidePlayable(path[index], radius) ||
                !insidePlayable(path[index + 1], radius)
            ) return null
            if (path[index].distanceTo(path[index + 1]) <= MIN_TRAVEL) return null
        }
        return path
    }

    private fun isPathClear(
        path: List<Vector2D>,
        isClear: (Vector2D, Vector2D) -> Boolean
    ): Boolean {
        for (index in 0 until path.size - 1) {
            if (!isClear(path[index], path[index + 1])) return false
        }
        return true
    }

    /**
     * The striker is a disc, so its centre reflects across the plane one radius
     * inside the physical cushion, not across the cushion edge itself.
     */
    private fun reflect(point: Vector2D, rail: Rail, radius: Float): Vector2D = when (rail) {
        Rail.LEFT -> Vector2D(2f * (minX + radius) - point.x, point.y)
        Rail.RIGHT -> Vector2D(2f * (maxX - radius) - point.x, point.y)
        Rail.TOP -> Vector2D(point.x, 2f * (minY + radius) - point.y)
        Rail.BOTTOM -> Vector2D(point.x, 2f * (maxY - radius) - point.y)
    }

    private fun intersection(
        from: Vector2D,
        to: Vector2D,
        rail: Rail,
        radius: Float
    ): Float? {
        val left = minX + radius
        val right = maxX - radius
        val top = minY + radius
        val bottom = maxY - radius
        val dx = to.x - from.x
        val dy = to.y - from.y
        return when (rail) {
            Rail.LEFT -> if (abs(dx) <= Vector2D.EPSILON) null else (left - from.x) / dx
            Rail.RIGHT -> if (abs(dx) <= Vector2D.EPSILON) null else (right - from.x) / dx
            Rail.TOP -> if (abs(dy) <= Vector2D.EPSILON) null else (top - from.y) / dy
            Rail.BOTTOM -> if (abs(dy) <= Vector2D.EPSILON) null else (bottom - from.y) / dy
        }
    }

    private fun onRail(point: Vector2D, rail: Rail, radius: Float): Boolean = when (rail) {
        Rail.LEFT, Rail.RIGHT ->
            point.y >= minY + radius && point.y <= maxY - radius

        Rail.TOP, Rail.BOTTOM ->
            point.x >= minX + radius && point.x <= maxX - radius
    }

    private fun insidePlayable(point: Vector2D, radius: Float): Boolean =
        point.x >= minX + radius && point.x <= maxX - radius &&
            point.y >= minY + radius && point.y <= maxY - radius

    private fun polylineLength(path: List<Vector2D>): Float {
        var length = 0f
        for (index in 0 until path.size - 1) {
            length += path[index].distanceTo(path[index + 1])
        }
        return length
    }

    private enum class Rail { LEFT, RIGHT, TOP, BOTTOM }

    private companion object {
        const val MIN_TRAVEL = 0.5f
    }
}
