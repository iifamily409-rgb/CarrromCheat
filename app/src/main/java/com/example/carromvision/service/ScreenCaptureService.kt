package com.example.carromvision.service

import android.app.Activity
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import androidx.core.app.ServiceCompat
import com.example.carromvision.overlay.OverlayManager
import org.opencv.android.OpenCVLoader

/**
 * Foreground mediaProjection service. It owns only Android lifecycle concerns:
 * notification, overlay lifetime, and the capture engine lifecycle.
 */
class ScreenCaptureService : Service() {

    private lateinit var overlayManager: OverlayManager
    private lateinit var captureEngine: VisionCaptureEngine
    private var openCvReady = false

    override fun onCreate() {
        super.onCreate()

        if (!startAsForeground()) {
            stopSelf()
            return
        }

        if (!Settings.canDrawOverlays(this)) {
            Log.e(TAG, "SYSTEM_ALERT_WINDOW is not granted; overlay cannot be mounted")
            stopSelf()
            return
        }

        // Must load the packaged OpenCV native runtime before constructing the
        // detection pipeline. initLocal() is the non-deprecated bundled loader.
        openCvReady = OpenCVLoader.initLocal()
        if (!openCvReady) {
            Log.e(TAG, "OpenCV native library initialization failed")
            stopSelf()
            return
        }

        overlayManager = OverlayManager(this)
        // Service.onCreate runs on the main thread; OverlayManager also marshals
        // these calls defensively, so both windows are attached before capture.
        overlayManager.show()
        overlayManager.showFloatingMenu()

        captureEngine = VisionCaptureEngine(
            context = this,
            overlayManager = overlayManager,
            onProjectionStopped = { stopSelf() }
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resultCode = intent?.getIntExtra(
            EXTRA_RESULT_CODE,
            Activity.RESULT_CANCELED
        ) ?: Activity.RESULT_CANCELED
        val resultData = intent?.projectionConsentData()

        if (!openCvReady || resultCode != Activity.RESULT_OK || resultData == null) {
            Log.e(TAG, "Missing OpenCV or MediaProjection consent state")
            stopSelf()
            return START_NOT_STICKY
        }

        if (!captureEngine.start(resultCode, resultData)) {
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startAsForeground(): Boolean = try {
        CaptureNotification.ensureChannel(this)
        val notification = CaptureNotification.build(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                CaptureNotification.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(CaptureNotification.NOTIFICATION_ID, notification)
        }
        true
    } catch (error: RuntimeException) {
        Log.e(TAG, "Unable to enter foreground state", error)
        false
    }

    private fun Intent.projectionConsentData(): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(EXTRA_RESULT_DATA)
        }

    override fun onDestroy() {
        if (::captureEngine.isInitialized) captureEngine.stop()
        if (::overlayManager.isInitialized) overlayManager.destroy()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        private const val TAG = "ScreenCaptureService"
    }
}
