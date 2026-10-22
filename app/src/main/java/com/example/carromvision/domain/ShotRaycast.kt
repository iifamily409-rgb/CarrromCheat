package com.example.carromvision.domain

import kotlin.math.sqrt

internal data class RayHit(val coin: CoinEntity, val ghost: Vector2D)

/**
 * Pure raycast helpers for the aim line. A hit is the first coin whose body is
 * intersected by a disc of the striker's radius travelling along [direction].
 */
internal object ShotRaycast {
    fun nearestCoin(striker: CoinEntity, coins: List<CoinEntity>): CoinEntity? =
        coins.filter { it.id != striker.id }
            .minByOrNull { it.position.distanceTo(striker.position) }

    fun firstHit(
        striker: CoinEntity,
        coins: List<CoinEntity>,
        direction: Vector2D,
        maxLength: Float
    ): RayHit? {
        var nearest: RayHit? = null
        var nearestDistance = maxLength
        for (coin in coins) {
            if (coin.id == striker.id) continue
            val offset = coin.position - striker.position
            val projection = offset.dot(direction)
            if (projection < -striker.radius) continue

            val contact = striker.radius + coin.radius
            val perpendicularSq = offset.dot(offset) - projection * projection
            if (perpendicularSq > contact * contact) continue

            val entry = projection - sqrt(contact * contact - perpendicularSq)
            if (entry > maxLength) continue
            val distance = entry.coerceAtLeast(0f)
            if (distance > nearestDistance) continue
            nearestDistance = distance
            nearest = RayHit(coin, striker.position + direction * distance)
        }
        return nearest
    }
}
