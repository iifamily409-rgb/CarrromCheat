package com.example.carromvision.domain

import kotlin.math.pow
import kotlin.math.sqrt

data class CollisionResult(
    val normal: Vector2D,
    val coinVelocity: Vector2D,
    val strikerDeflection: Vector2D,
    val normalSpeed: Float,
    val tangentSpeed: Float
)

/**
 * Closed-form physics shared by the solver and unit tests.
 *
 * Speeds are metres/second. The board is represented in logical units, so
 * stopping distances are converted with a 0.74 m playing-surface width.
 */
object CollisionPhysics {
    const val MU = 0.12f
    const val GRAVITY = 9.81f
    const val VISCOUS = 0.10f
    const val V_MIN = 0.15f
    const val V_MAX = 3.2f
    const val POWER_EXPONENT = 1.4f
    const val RESTITUTION = 0.90f

    // ICF mass ratio 15 g : 5 g. The ratio form keeps the closed-form impulse
    // coefficients exact while the gram constants document the hardware.
    const val STRIKER_MASS_GRAMS = 15f
    const val COIN_MASS_GRAMS = 5f
    const val STRIKER_MASS = STRIKER_MASS_GRAMS / COIN_MASS_GRAMS
    const val COIN_MASS = 1f
    const val RAIL_RESTITUTION = 0.75f
    const val TANGENTIAL_LOSS = 0.95f
    const val UNITS_PER_METER = 1000f / 0.74f

    // (1 + e) * m_s / (m_s + m_c) = 1.425
    const val COIN_GAIN =
        (1f + RESTITUTION) * STRIKER_MASS_GRAMS /
            (STRIKER_MASS_GRAMS + COIN_MASS_GRAMS)

    // (m_s - e * m_c) / (m_s + m_c) = 0.525
    const val STRIKER_LOSS =
        (STRIKER_MASS_GRAMS - RESTITUTION * COIN_MASS_GRAMS) /
            (STRIKER_MASS_GRAMS + COIN_MASS_GRAMS)

    fun easedPower(power: Float): Float =
        power.coerceIn(0f, 1f).pow(POWER_EXPONENT)

    fun velocityForPower(power: Float): Float =
        V_MIN + (V_MAX - V_MIN) * easedPower(power)

    /** x_stop = v0^2 / (2 * MU * G), converted to board units. */
    fun stoppingDistanceForVelocity(velocity: Float): Float {
        if (velocity <= 0f) return 0f
        return (velocity * velocity / (2f * MU * GRAVITY)) * UNITS_PER_METER
    }

    fun stoppingDistanceForPower(power: Float): Float =
        stoppingDistanceForVelocity(velocityForPower(power))

    /**
     * Speed remaining after travelling [distanceUnits] under Coulomb friction.
     * v^2 = v0^2 - 2 * MU * g * x, with x converted from board units to metres.
     */
    fun velocityAfterDistance(initialSpeed: Float, distanceUnits: Float): Float {
        if (initialSpeed <= 0f) return 0f
        if (distanceUnits <= 0f) return initialSpeed
        val distanceMeters = distanceUnits / UNITS_PER_METER
        val squared = initialSpeed * initialSpeed -
            2f * MU * GRAVITY * distanceMeters
        return sqrt(squared.coerceAtLeast(0f))
    }

    fun deceleration(speed: Float): Float =
        MU * GRAVITY + VISCOUS * speed.coerceAtLeast(0f)

    /**
     * The contact normal runs from the ghost-ball centre to the coin centre.
     * The coin receives 1.425 * v_normal; the striker keeps 0.525 * v_tangent.
     */
    fun resolveCollision(
        incomingVelocity: Vector2D,
        contactNormal: Vector2D
    ): CollisionResult {
        val normal = contactNormal.normalized()
        if (normal.isZero()) {
            return CollisionResult(Vector2D.ZERO, Vector2D.ZERO, Vector2D.ZERO, 0f, 0f)
        }

        val normalSpeed = incomingVelocity.dot(normal)
        if (normalSpeed <= 0f) {
            return CollisionResult(normal, Vector2D.ZERO, Vector2D.ZERO, 0f, 0f)
        }

        val normalComponent = normal * normalSpeed
        val tangent = incomingVelocity - normalComponent
        return CollisionResult(
            normal = normal,
            coinVelocity = normal * (COIN_GAIN * normalSpeed),
            strikerDeflection = tangent * STRIKER_LOSS,
            normalSpeed = normalSpeed,
            tangentSpeed = tangent.magnitude()
        )
    }

    /** Inward rail normal; normal speed is inverted with 0.75 restitution. */
    fun reflectOnRail(velocity: Vector2D, inwardNormal: Vector2D): Vector2D {
        val normal = inwardNormal.normalized()
        if (normal.isZero()) return velocity
        val normalSpeed = velocity.dot(normal)
        val tangent = velocity - normal * normalSpeed
        return normal * (-RAIL_RESTITUTION * normalSpeed) +
            tangent * TANGENTIAL_LOSS
    }
}
