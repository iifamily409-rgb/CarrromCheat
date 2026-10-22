package com.example.carromvision.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CollisionPhysicsTest {

    @Test
    fun icfDimensionsMatchSpecification() {
        assertEquals(49f, BoardSpec.CONTACT_DISTANCE, 0.0001f)
        assertEquals(1000f, BoardSpec.LOGICAL_SIZE, 0.0001f)
        assertEquals(1f, BoardSpec.LOGICAL_ASPECT, 0.0001f)
        assertEquals(21.5f, BoardSpec.COIN_RADIUS, 0.0001f)
        assertEquals(27.5f, BoardSpec.STRIKER_RADIUS, 0.0001f)
        assertEquals(30f, BoardSpec.POCKET_RADIUS, 0.0001f)
        assertEquals(45f, BoardSpec.POCKET_INSET, 0.0001f)
        assertEquals(15f, CollisionPhysics.STRIKER_MASS_GRAMS, 0.0001f)
        assertEquals(5f, CollisionPhysics.COIN_MASS_GRAMS, 0.0001f)
        assertEquals(3f, CollisionPhysics.STRIKER_MASS, 0.0001f)
    }

    @Test
    fun powerCurveUsesEasedNormalizedDrag() {
        assertEquals(0.15f, CollisionPhysics.velocityForPower(0f), 0.0001f)
        assertEquals(3.2f, CollisionPhysics.velocityForPower(1f), 0.0001f)

        val expectedT = Math.pow(0.5, 1.4).toFloat()
        assertEquals(expectedT, CollisionPhysics.easedPower(0.5f), 0.0001f)
        assertEquals(
            0.15f + 3.05f * expectedT,
            CollisionPhysics.velocityForPower(0.5f),
            0.001f
        )
    }

    @Test
    fun stoppingDistanceUsesCoulombFormulaAndLogicalUnits() {
        val expected = (4f / (2f * 0.12f * 9.81f)) * (1000f / 0.74f)
        assertEquals(
            expected,
            CollisionPhysics.stoppingDistanceForVelocity(2f),
            1f
        )
        assertTrue(
            CollisionPhysics.stoppingDistanceForPower(0.15f) <
                CollisionPhysics.stoppingDistanceForPower(1f)
        )
        assertTrue(CollisionPhysics.deceleration(2f) > CollisionPhysics.MU * CollisionPhysics.GRAVITY)
    }

    @Test
    fun frictionDecaysImpactSpeedAlongTheStrikerPath() {
        val initial = 2f
        assertEquals(initial, CollisionPhysics.velocityAfterDistance(initial, 0f), 0.0001f)
        assertTrue(CollisionPhysics.velocityAfterDistance(initial, 500f) < initial)
        assertEquals(
            0f,
            CollisionPhysics.velocityAfterDistance(
                initial,
                CollisionPhysics.stoppingDistanceForVelocity(initial)
            ),
            0.01f
        )
    }

    @Test
    fun elasticCollisionMatchesThreeToOneMassRatio() {
        val headOn = CollisionPhysics.resolveCollision(
            Vector2D(2f, 0f), Vector2D(1f, 0f)
        )
        assertEquals(2.85f, headOn.coinVelocity.x, 0.0001f)
        assertEquals(0f, headOn.coinVelocity.y, 0.0001f)
        assertEquals(0f, headOn.strikerDeflection.magnitude(), 0.0001f)

        val cut = CollisionPhysics.resolveCollision(
            Vector2D(1f, 1f), Vector2D(1f, 0f)
        )
        assertEquals(1.425f, cut.coinVelocity.x, 0.0001f)
        assertEquals(0.525f, cut.strikerDeflection.y, 0.0001f)
    }

    @Test
    fun railReflectionAppliesRestitutionAndTangentialLoss() {
        val reflected = CollisionPhysics.reflectOnRail(
            Vector2D(1f, -2f), Vector2D(0f, 1f)
        )
        assertEquals(0.95f, reflected.x, 0.0001f)
        assertEquals(1.5f, reflected.y, 0.0001f)
    }

    @Test
    fun cushionCandidateUsesBankRouteWhenDirectCoinLineIsBlocked() {
        val geometry = ShotGeometry()
        val striker = CoinEntity(0, Vector2D(500f, 850f), BoardSpec.STRIKER_RADIUS)
        val coin = CoinEntity(1, Vector2D(500f, 500f), BoardSpec.COIN_RADIUS)
        val blocker = CoinEntity(2, Vector2D(260f, 300f), BoardSpec.COIN_RADIUS)
        val pocket = Vector2D(BoardSpec.POCKET_INSET, BoardSpec.POCKET_INSET)

        val candidate = requireNotNull(
            geometry.buildCandidate(
                striker = striker,
                coin = coin,
                pocket = pocket,
                coins = listOf(coin, blocker),
                allowBank = true,
                maxLength = CollisionPhysics.stoppingDistanceForPower(1f),
                power = 1f,
                clearanceScale = 1f
            )
        )

        assertTrue(candidate.trajectory.isBankShot)
        assertTrue(candidate.trajectory.coinPath.size >= 3)
        assertEquals(pocket, candidate.trajectory.coinPath.last())
    }

    @Test
    fun multiPuckAssistsReturnDistinctColoredRoutes() {
        val solver = PhysicsSolver()
        val striker = CoinEntity(0, Vector2D(500f, 850f), BoardSpec.STRIKER_RADIUS)
        val coins = listOf(
            CoinEntity(1, Vector2D(350f, 500f), BoardSpec.COIN_RADIUS),
            CoinEntity(2, Vector2D(650f, 500f), BoardSpec.COIN_RADIUS),
            CoinEntity(3, Vector2D(500f, 650f), BoardSpec.COIN_RADIUS, isQueen = true)
        )

        val assists = solver.solveMultiPuckAssists(striker, coins)

        assertTrue(assists.isNotEmpty())
        assertEquals(assists.size, assists.map { it.color }.toSet().size)
        assertEquals(assists.size, assists.map { it.targetCoinPos }.toSet().size)
        for (assist in assists) {
            assertFalse(assist.color == TrajectoryColor.WHITE)
            assertEquals(striker.position, assist.strikerPath.first())
            assertEquals(assist.pocketTarget, assist.coinPath.last())
            assertTrue(assist.ghostBallCenter.x in 0f..1000f)
            assertTrue(assist.ghostBallCenter.y in 0f..1000f)
        }
    }
}
