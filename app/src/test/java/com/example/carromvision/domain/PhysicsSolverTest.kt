package com.example.carromvision.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhysicsSolverTest {

    @Test
    fun vectorMathSupportsShotGeometry() {
        val a = Vector2D(3f, 4f)
        assertEquals(5f, a.magnitude(), 0.0001f)
        assertEquals(0.6f, a.normalized().x, 0.0001f)
        assertEquals(0.8f, a.normalized().y, 0.0001f)
        assertEquals(11f, a.dot(Vector2D(1f, 2f)), 0.0001f)
        assertEquals(Vector2D(4f, 6f), a + Vector2D(1f, 2f))
    }

    @Test
    fun reverseRaycastPlacesGhostBallOnPocketLine() {
        val solver = PhysicsSolver()
        val striker = CoinEntity(0, Vector2D(500f, 850f), 26f)
        val coin = CoinEntity(1, Vector2D(500f, 500f), 22f)

        val shot =  requireNotNull(solver.solveBestShot(striker, listOf(coin)))

        val pocketDirection = (shot.pocketTarget - coin.position).normalized()
        val expectedGhost = coin.position - pocketDirection * (striker.radius + coin.radius)

        assertEquals(expectedGhost.x, shot.ghostBallCenter.x, 0.01f)
        assertEquals(expectedGhost.y, shot.ghostBallCenter.y, 0.01f)
        assertEquals(shot.ghostBallCenter, shot.strikerPath.last())
        assertEquals(shot.pocketTarget, shot.coinPath.last())
        assertEquals(striker.position, shot.strikerPath.first())
        assertFalse(shot.isBankShot)
    }

    @Test
    fun cushionSolverMirrorsAcrossStrikerCentrePlane() {
        val solver = CushionSolver(0f, 0f, 1000f, 1000f)
        val path = requireNotNull(
            solver.findPath(
                start = Vector2D(500f, 800f),
                goal = Vector2D(100f, 500f),
                radius = 20f,
                isClear = { _, _ -> true }
            )
        )

        assertEquals(3, path.size)
        assertEquals(Vector2D(500f, 800f), path.first())
        assertEquals(Vector2D(100f, 500f), path.last())
        assertEquals(20f, path[1].x, 0.5f)
        assertTrue(path[1].y in 20f..980f)
    }

    @Test
    fun cushionSolverAcceptsBounceCloseToTheStartingRail() {
        val solver = CushionSolver(0f, 0f, 1000f, 1000f)
        val path = requireNotNull(
            solver.findPath(
                start = Vector2D(500f, 970f),
                goal = Vector2D(100f, 900f),
                radius = 20f,
                isClear = { _, _ -> true }
            )
        )

        assertEquals(3, path.size)
        assertEquals(980f, path[1].y, 0.01f)
    }

    @Test
    fun solveShotClampsStrikerOnTopBaselineAndKeepsTrajectory() {
        val solver = PhysicsSolver()
        val striker = CoinEntity(0, Vector2D(500f, 2f), 26f)
        val coin = CoinEntity(1, Vector2D(500f, 500f), 22f)

        val shot = requireNotNull(
            solver.solveShot(striker, listOf(coin), Vector2D(0f, 1f), true, 0.7f)
        )

        assertTrue(shot.strikerStart.y >= 13f)
        assertEquals(shot.strikerStart, shot.strikerPath.first())
        assertTrue(shot.strikerPath.size >= 2)
        assertTrue(shot.coinPath.size >= 2)
    }

    @Test
    fun solveShotProjectsFallbackWhenNoPocketRouteIsAvailable() {
        val solver = PhysicsSolver()
        val striker = CoinEntity(0, Vector2D(500f, 500f), 26f)
        // In contact with the striker, so every direct-pocket candidate is skipped.
        val coin = CoinEntity(1, Vector2D(510f, 500f), 22f)

        val shot = requireNotNull(solver.solveShot(striker, listOf(coin), null, false, 1f))

        assertEquals(striker.position, shot.strikerStart)
        assertEquals(2, shot.strikerPath.size)
        assertEquals(2, shot.coinPath.size)
        assertFalse(shot.isBankShot)
    }

    @Test
    fun lowPowerRaycastClampsToCoulombStoppingDistance() {
        val solver = PhysicsSolver()
        val striker = CoinEntity(0, Vector2D(500f, 950f), BoardSpec.STRIKER_RADIUS)
        val coin = CoinEntity(1, Vector2D(500f, 100f), BoardSpec.COIN_RADIUS)
        val power = 0.02f

        val shot = requireNotNull(
            solver.solveShot(striker, listOf(coin), Vector2D(0f, -1f), true, power)
        )

        val travelled = shot.strikerPath.first().distanceTo(shot.strikerPath.last())
        val maxLength = CollisionPhysics.stoppingDistanceForPower(power)
        assertTrue(travelled > 0f)
        assertTrue(travelled <= maxLength + 0.5f)
        assertTrue(shot.coinPath.isEmpty())
    }

    @Test
    fun activeDragRayFollowsAimInsteadOfUnrelatedPot() {
        val solver = PhysicsSolver()
        val striker = CoinEntity(0, Vector2D(500f, 850f), BoardSpec.STRIKER_RADIUS)
        val coin = CoinEntity(1, Vector2D(100f, 500f), BoardSpec.COIN_RADIUS)

        val shot = requireNotNull(
            solver.solveShot(striker, listOf(coin), Vector2D(0f, -1f), true, 0.5f)
        )

        assertEquals(striker.position.x, shot.strikerPath.last().x, 0.001f)
        assertTrue(shot.strikerPath.last().y < striker.position.y)
        assertTrue(shot.coinPath.isEmpty())
    }
}
