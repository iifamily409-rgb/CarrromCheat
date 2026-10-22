package com.example.carromvision.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.RectF
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import com.example.carromvision.domain.ShotTrajectory

class OverlayManager(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val overlayView = AimOverlayView(context)
    private val floatingMenuManager = FloatingMenuManager(context) { updatedConfig ->
        applyConfig(updatedConfig)
    }
    private var isShown = false
    private var lastShot: ShotTrajectory? = null
    private var lastAssists: List<ShotTrajectory> = emptyList()
    private var lastBoardRect: RectF? = null
    private var lastLogicalSize = 0f

    @Volatile
    private var config = OverlayConfig()

    private val layoutParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            fitInsetsTypes = 0
        }
    }

    fun show() = runOnMain {
        if (!Settings.canDrawOverlays(context)) {
            Log.e(TAG, "SYSTEM_ALERT_WINDOW is not granted")
            return@runOnMain
        }
        if (!isShown) {
            overlayView.config = config
            try {
                windowManager.addView(overlayView, layoutParams)
                isShown = true
                Log.i(TAG, "Full-screen aim overlay mounted")
            } catch (error: RuntimeException) {
                Log.e(TAG, "Unable to add aiming overlay", error)
            }
        }
        // The control bubble is independent of the full-screen overlay so it
        // still appears when the trajectory layer itself fails to attach.
        floatingMenuManager.show()
    }

    /**
     * Explicitly attaches the interactive configuration bubble on the main thread.
     * Idempotent, so callers can invoke it safely during service initialization.
     */
    fun showFloatingMenu() = runOnMain {
        if (!Settings.canDrawOverlays(context)) {
            Log.e(TAG, "Cannot mount floating menu without overlay permission")
            return@runOnMain
        }
        floatingMenuManager.show()
    }

    fun hide() = runOnMain { removeOverlay() }

    fun update(
        shot: ShotTrajectory?,
        assists: List<ShotTrajectory>,
        boardRect: RectF,
        logicalSize: Float
    ) {
        // The vision worker runs at camera rate; identical results must not
        // enqueue a redundant main-looper message and invalidate.
        if (shot == lastShot && assists == lastAssists &&
            lastBoardRect == boardRect && logicalSize == lastLogicalSize
        ) return
        lastShot = shot
        lastAssists = assists
        lastBoardRect = RectF(boardRect)
        lastLogicalSize = logicalSize
        runOnMain { overlayView.update(shot, assists, boardRect, logicalSize) }
    }

    fun clear() {
        resetFrameCache()
        runOnMain { overlayView.clear() }
    }

    private fun resetFrameCache() {
        lastShot = null
        lastAssists = emptyList()
        lastBoardRect = null
        lastLogicalSize = 0f
    }

    fun updateConfig(updatedConfig: OverlayConfig) = applyConfig(updatedConfig)

    private fun applyConfig(updatedConfig: OverlayConfig) {
        config = updatedConfig
        runOnMain { overlayView.config = updatedConfig }
    }

    fun destroy() = runOnMain {
        removeOverlay()
        overlayView.clear()
        mainHandler.removeCallbacksAndMessages(null)
    }

    private fun removeOverlay() {
        if (isShown) {
            try {
                windowManager.removeViewImmediate(overlayView)
            } catch (error: RuntimeException) {
                Log.w(TAG, "Unable to remove aiming overlay", error)
            } finally {
                isShown = false
                resetFrameCache()
                overlayView.clear()
            }
        }
        floatingMenuManager.destroy()
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }

    private companion object {
        const val TAG = "OverlayManager"
    }
}
