package com.example.carromvision.domain

internal data class ShotCandidate(val trajectory: ShotTrajectory, val score: Float)

/** Pure geometry and collision candidate construction for the shot solver. */
internal class ShotGeometry(
    private val boardSize: Float = BoardSpec.LOGICAL_SIZE,
    pocketInset: Float = BoardSpec.POCKET_INSET
) {
    private val minX = 0f
    private val minY = 0f
    private val maxX = boardSize
    private val maxY = boardSize
    val pockets = listOf(
        Vector2D(pocketInset, pocketInset),
        Vector2D(maxX - pocketInset, minY + pocketInset),
        Vector2D(minX + pocketInset, maxY - pocketInset),
        Vector2D(maxX - pocketInset, maxY - pocketInset)
    )
    private val cushions = CushionSolver(minX, minY, maxX, maxY)

    fun buildCandidate(
        striker: CoinEntity,
        coin: CoinEntity,
        pocket: Vector2D,
        coins: List<CoinEntity>,
        allowBank: Boolean,
        maxLength: Float,
        power: Float,
        clearanceScale: Float
    ): ShotCandidate? {
        if (coin.id == striker.id) return null
        val contact = striker.radius + coin.radius
        if (striker.position.distanceTo(coin.position) <= contact) return null

        val pocketDirection = (pocket - coin.position).normalized()
        if (pocketDirection.isZero()) return null
        val ghost = coin.position - pocketDirection * contact
        if (!insidePlayable(ghost, striker.radius)) return null

        val strikerPath = when {
            segmentClear(
                striker.position, ghost, striker.id, coins,
                striker.radius, clearanceScale
            ) -> listOf(striker.position, ghost)

            allowBank -> cushions.findPath(
                striker.position, ghost, striker.radius
            ) { from, to ->
                segmentClear(from, to, striker.id, coins, striker.radius, clearanceScale)
            } ?: return null

            else -> return null
        }
        val strikerLength = polylineLength(strikerPath)
        if (strikerLength <= MIN_TRAVEL || strikerLength > maxLength + MIN_TRAVEL) {
            return null
        }

        val coinPath = if (segmentClear(
                coin.position, pocket, coin.id, coins, coin.radius, clearanceScale
            )
        ) {
            listOf(coin.position, pocket)
        } else {
            cushions.findPath(coin.position, pocket, coin.radius) { from, to ->
                segmentClear(from, to, coin.id, coins, coin.radius, clearanceScale)
            } ?: return null
        }

        val incoming = (strikerPath.last() - strikerPath[strikerPath.lastIndex - 1]).normalized()
        val normal = (coin.position - ghost).normalized()
        val impactSpeed = CollisionPhysics.velocityAfterDistance(
            CollisionPhysics.velocityForPower(power), strikerLength
        )
        val collision = CollisionPhysics.resolveCollision(
            incoming * impactSpeed, normal
        )
        if (collision.normalSpeed <= MIN_SPEED) return null

        val coinStop = CollisionPhysics.stoppingDistanceForVelocity(
            collision.coinVelocity.magnitude()
        )
        val coinLength = polylineLength(coinPath)
        if (coinLength <= MIN_TRAVEL || coinLength > coinStop + MIN_TRAVEL) return null

        val isBank = strikerPath.size > 2 || coinPath.size > 2
        val score = strikerLength + coinLength +
            if (isBank) BANK_PENALTY else 0f -
            if (coin.isQueen) QUEEN_BONUS else 0f
        return ShotCandidate(
            trajectory = ShotTrajectory(
                strikerStart = striker.position,
                ghostBallCenter = ghost,
                targetCoinPos = coin.position,
                pocketTarget = pocket,
                coinPath = coinPath,
                strikerPath = strikerPath,
                isBankShot = isBank,
                strikerDeflection = buildDeflection(ghost, collision.strikerDeflection)
            ),
            score = score
        )
    }

    /**
     * Raycasts the drag direction and stops at the first contact or at the
     * power-limited stopping distance. Used while dragging and as the final
     * fallback when no pocket route exists.
     */
    fun fallback(
        striker: CoinEntity,
        coins: List<CoinEntity>,
        aim: Vector2D?,
        maxLength: Float,
        power: Float
    ): ShotTrajectory? {
        val target = ShotRaycast.nearestCoin(striker, coins)
        val direction = aim
            ?.normalized()
            ?.takeIf { !it.isZero() }
            ?: target?.let { (it.position - striker.position).normalized() }
                ?.takeIf { !it.isZero() }
            ?: Vector2D(0f, 1f)
        val travel = maxLength.coerceAtLeast(MIN_TRAVEL)
        val hit = ShotRaycast.firstHit(striker, coins, direction, travel)
        val ghost = hit?.ghost
            ?: clipToBoard(striker.position + direction * travel, striker.radius)
        val pocket = hit?.coin?.let { coin ->
            pockets.minByOrNull { it.distanceTo(coin.position) }
        }
        val coinPath = if (hit != null && pocket != null && segmentClear(
                hit.coin.position, pocket, hit.coin.id, coins, hit.coin.radius, 1f
            )
        ) {
            listOf(hit.coin.position, pocket)
        } else {
            emptyList()
        }
        val normal = hit?.let { (it.coin.position - it.ghost).normalized() } ?: Vector2D.ZERO
        val impactSpeed = CollisionPhysics.velocityAfterDistance(
            CollisionPhysics.velocityForPower(power),
            striker.position.distanceTo(ghost)
        )
        val collision = if (hit != null && !normal.isZero()) {
            CollisionPhysics.resolveCollision(direction * impactSpeed, normal)
        } else {
            null
        }
        return ShotTrajectory(
            strikerStart = striker.position,
            ghostBallCenter = ghost,
            targetCoinPos = hit?.coin?.position ?: ghost,
            pocketTarget = pocket ?: ghost,
            coinPath = coinPath,
            strikerPath = listOf(striker.position, ghost),
            isBankShot = false,
            strikerDeflection = collision
                ?.let { buildDeflection(ghost, it.strikerDeflection) }
                ?: emptyList()
        )
    }

    fun clampToPlayable(striker: CoinEntity): CoinEntity {
        val margin = striker.radius * 0.5f
        val clamped = Vector2D(
            striker.position.x.coerceIn(minX + margin, maxX - margin),
            striker.position.y.coerceIn(minY + margin, maxY - margin)
        )
        return if (clamped == striker.position) striker else striker.copy(position = clamped)
    }

    fun playableCoins(
        striker: CoinEntity,
        coins: List<CoinEntity>
    ): List<CoinEntity> = coins.filter {
        it.id != striker.id && insidePlayable(it.position, it.radius)
    }

    private fun buildDeflection(start: Vector2D, velocity: Vector2D): List<Vector2D> {
        val speed = velocity.magnitude()
        if (speed <= MIN_SPEED) return emptyList()
        val distance = CollisionPhysics.stoppingDistanceForVelocity(speed)
            .coerceAtMost(boardSize * DEFLECTION_FRACTION)
        val end = clipToBoard(
            start + velocity.normalized() * distance, BoardSpec.COIN_RADIUS
        )
        return if (start.distanceTo(end) <= MIN_TRAVEL) emptyList() else listOf(start, end)
    }

    private fun clipToBoard(point: Vector2D, radius: Float): Vector2D = Vector2D(
        point.x.coerceIn(minX + radius, maxX - radius),
        point.y.coerceIn(minY + radius, maxY - radius)
    )

    private fun segmentClear(
        from: Vector2D,
        to: Vector2D,
        excludedId: Int,
        coins: List<CoinEntity>,
        movingRadius: Float,
        clearanceScale: Float
    ): Boolean {
        for (other in coins) {
            if (other.id == excludedId) continue
            val clearance = (movingRadius + other.radius) * clearanceScale
            if (distanceToSegment(other.position, from, to) < clearance - CLEARANCE_EPSILON) {
                return false
            }
        }
        return true
    }

    private fun distanceToSegment(point: Vector2D, a: Vector2D, b: Vector2D): Float {
        val ab = b - a
        val lengthSquared = ab.dot(ab)
        if (lengthSquared <= Vector2D.EPSILON) return point.distanceTo(a)
        val t = ((point - a).dot(ab) / lengthSquared).coerceIn(0f, 1f)
        return point.distanceTo(a + ab * t)
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

    private companion object {
        const val BANK_PENALTY = 20f
        const val QUEEN_BONUS = 80f
        const val MIN_TRAVEL = 0.5f
        const val MIN_SPEED = 0.02f
        const val CLEARANCE_EPSILON = 0.05f
        const val DEFLECTION_FRACTION = 0.35f
    }
}
