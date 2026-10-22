package com.example.carromvision.domain

/**
 * Deterministic shot solver for the 1000 x 1000 ICF logical surface.
 *
 * A shot is built backwards from a pocket: the ghost ball is one contact
 * distance away from the coin, then a direct or cushion route is found for the
 * striker. The collision result drives the tangent deflection and the target
 * coin's stopping distance.
 */
class PhysicsSolver(
    boardSize: Float = BoardSpec.LOGICAL_SIZE,
    pocketInset: Float = BoardSpec.POCKET_INSET
) {
    private val geometry = ShotGeometry(boardSize, pocketInset)
    private val pockets = geometry.pockets
    private val assistColors = listOf(
        TrajectoryColor.RED,
        TrajectoryColor.BLUE,
        TrajectoryColor.YELLOW,
        TrajectoryColor.ORANGE,
        TrajectoryColor.GREEN,
        TrajectoryColor.PURPLE
    )

    fun solveBestShot(striker: CoinEntity, coins: List<CoinEntity>): ShotTrajectory? =
        solveShot(striker, coins, null, false, DEFAULT_POWER)

    fun solveShot(
        striker: CoinEntity?,
        coins: List<CoinEntity>,
        aimDir: Vector2D?,
        isDragging: Boolean,
        power: Float = 1f
    ): ShotTrajectory? {
        if (striker == null) return null
        val start = geometry.clampToPlayable(striker)
        val targets = geometry.playableCoins(start, coins)
        if (targets.isEmpty()) return null

        val aim = aimDir?.normalized()?.takeIf { !it.isZero() }
        val cappedPower = power.coerceIn(0f, 1f)
        val maxStrikerLength = CollisionPhysics.stoppingDistanceForPower(cappedPower)
        if (isDragging && aim != null) {
            // While the player is actively dragging, never substitute an
            // unrelated "best" pot: the ray must follow the physical drag.
            return findAimedShot(start, targets, aim, maxStrikerLength, cappedPower)
                ?: geometry.fallback(start, targets, aim, maxStrikerLength, cappedPower)
        }
        return bestFor(start, targets, false, maxStrikerLength, cappedPower)
            ?: bestFor(start, targets, true, maxStrikerLength, cappedPower)
            ?: bestFor(
                start, targets, false, maxStrikerLength,
                cappedPower, RELAXED_CLEARANCE
            )
            ?: bestFor(
                start, targets, true, maxStrikerLength,
                cappedPower, RELAXED_CLEARANCE
            )
            ?: geometry.fallback(start, targets, aim, maxStrikerLength, cappedPower)
    }

    /**
     * One best pot route per coin, covering direct and cushion routes. The
     * result is sorted by difficulty and assigned the six Cheto palette colors.
     */
    fun solveMultiPuckAssists(
        striker: CoinEntity,
        coins: List<CoinEntity>
    ): List<ShotTrajectory> {
        val start = geometry.clampToPlayable(striker)
        val targets = geometry.playableCoins(start, coins)
        val power = ASSIST_POWER
        val maxLength = CollisionPhysics.stoppingDistanceForPower(power)
        val bestByCoin = LinkedHashMap<Int, ShotCandidate>()

        for (coin in targets) {
            var best: ShotCandidate? = null
            for (pocket in pockets) {
                val candidate = geometry.buildCandidate(
                    start, coin, pocket, coins, true, maxLength, power, 1f
                ) ?: continue
                if (best == null || candidate.score < best.score) best = candidate
            }
            if (best != null) bestByCoin[coin.id] = best
        }

        return bestByCoin.values
            .sortedBy { it.score }
            .take(assistColors.size)
            .mapIndexed { index, candidate ->
                candidate.trajectory.copy(color = assistColors[index])
            }
    }

    private fun findAimedShot(
        striker: CoinEntity,
        coins: List<CoinEntity>,
        aim: Vector2D,
        maxLength: Float,
        power: Float
    ): ShotTrajectory? {
        var best: ShotCandidate? = null
        for (coin in coins) {
            for (pocket in pockets) {
                val candidate = geometry.buildCandidate(
                    striker, coin, pocket, coins, true, maxLength, power, 1f
                ) ?: continue
                val toGhost =
                    (candidate.trajectory.ghostBallCenter - striker.position).normalized()
                val aimError = 1f - toGhost.dot(aim).coerceIn(-1f, 1f)
                if (aimError > MAX_AIM_ERROR) continue
                val scored = candidate.copy(score = candidate.score + aimError * 2400f)
                if (best == null || scored.score < best.score) best = scored
            }
        }
        return best?.trajectory
    }

    private fun bestFor(
        striker: CoinEntity,
        coins: List<CoinEntity>,
        allowBank: Boolean,
        maxLength: Float,
        power: Float,
        clearanceScale: Float = 1f
    ): ShotTrajectory? {
        var best: ShotCandidate? = null
        for (coin in coins) {
            for (pocket in pockets) {
                val candidate = geometry.buildCandidate(
                    striker, coin, pocket, coins, allowBank,
                    maxLength, power, clearanceScale
                ) ?: continue
                if (best == null || candidate.score < best.score) best = candidate
            }
        }
        return best?.trajectory
    }

    companion object {
        const val DEFAULT_BOARD_SIZE = BoardSpec.LOGICAL_SIZE
        const val DEFAULT_POWER = 0.85f
        private const val ASSIST_POWER = 1f
        private const val MAX_AIM_ERROR = 0.24f
        private const val RELAXED_CLEARANCE = 0.6f
    }
}
