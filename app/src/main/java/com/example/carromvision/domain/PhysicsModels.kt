package com.example.carromvision.domain

import kotlin.math.sqrt

/** Visual categories used by the overlay; the domain layer stays Android-free. */
enum class TrajectoryColor { WHITE, RED, BLUE, YELLOW, ORANGE, GREEN, PURPLE }

data class Vector2D(val x: Float, val y: Float) {
    operator fun plus(other: Vector2D) = Vector2D(x + other.x, y + other.y)
    operator fun minus(other: Vector2D) = Vector2D(x - other.x, y - other.y)
    operator fun times(scalar: Float) = Vector2D(x * scalar, y * scalar)
    operator fun div(scalar: Float) = Vector2D(x / scalar, y / scalar)

    fun dot(other: Vector2D): Float = x * other.x + y * other.y
    fun cross(other: Vector2D): Float = x * other.y - y * other.x
    fun magnitude(): Float = sqrt(x * x + y * y)
    fun distanceTo(other: Vector2D): Float = (this - other).magnitude()
    fun isZero(): Boolean = magnitude() <= EPSILON
    fun perpendicular(): Vector2D = Vector2D(-y, x)

    fun normalized(): Vector2D {
        val length = magnitude()
        return if (length > EPSILON) Vector2D(x / length, y / length) else ZERO
    }

    companion object {
        const val EPSILON = 1e-4f
        val ZERO = Vector2D(0f, 0f)
    }
}

data class CoinEntity(
    val id: Int,
    val position: Vector2D,
    val radius: Float,
    val isQueen: Boolean = false
)

data class ShotTrajectory(
    val strikerStart: Vector2D,
    val ghostBallCenter: Vector2D,
    val targetCoinPos: Vector2D,
    val pocketTarget: Vector2D,
    val coinPath: List<Vector2D>,
    val strikerPath: List<Vector2D>,
    val isBankShot: Boolean,
    val strikerDeflection: List<Vector2D> = emptyList(),
    val color: TrajectoryColor = TrajectoryColor.WHITE
)

/** ICF carrom dimensions expressed on the 1000 x 1000 logical surface. */
object BoardSpec {
    const val LOGICAL_SIZE = 1000f
    const val LOGICAL_ASPECT = 1f
    const val COIN_RADIUS = LOGICAL_SIZE * 0.0215f
    const val STRIKER_RADIUS = LOGICAL_SIZE * 0.0275f
    const val POCKET_RADIUS = LOGICAL_SIZE * 0.030f
    const val POCKET_INSET = 45f
    const val CONTACT_DISTANCE = COIN_RADIUS + STRIKER_RADIUS
}
