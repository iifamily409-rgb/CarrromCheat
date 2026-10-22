package com.example.carromvision.cv

import android.graphics.RectF
import com.example.carromvision.domain.ShotTrajectory

/** One frame's detection result. Immutable so it can cross thread boundaries. */
data class FrameDetection(
    val trajectory: ShotTrajectory?,
    val assists: List<ShotTrajectory>,
    val boardRect: RectF,
    val frameWidth: Int,
    val frameHeight: Int,
    val isMatchActive: Boolean
)
