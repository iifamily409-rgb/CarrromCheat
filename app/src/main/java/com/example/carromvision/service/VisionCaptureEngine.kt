package com.example.carromvision.service

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.WindowManager
import com.example.carromvision.cv.FrameProcessor
import com.example.carromvision.overlay.OverlayManager

/** MediaProjection -> ImageReader -> OpenCV -> Overlay pipeline. */
internal class VisionCaptureEngine(
    private val context: Context,
    private val overlayManager: OverlayManager,
    private val onProjectionStopped: () -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val frameProcessor = FrameProcessor()
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var workerThread: HandlerThread? = null
    private var workerHandler: Handler? = null

    @Volatile private var running = false
    private var lastFrameAtMs = 0L
    private var framesProcessed = 0L
    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            Log.i(TAG, "MediaProjection stopped")
            onProjectionStopped()
        }
    }

    fun start(resultCode: Int, resultData: Intent): Boolean {
        if (mediaProjection != null) return true
        val manager = context.getSystemService(MediaProjectionManager::class.java)
        val projection = try {
            manager.getMediaProjection(resultCode, resultData)
        } catch (error: RuntimeException) {
            Log.e(TAG, "Unable to obtain MediaProjection", error)
            null
        } ?: return false

        mediaProjection = projection
        projection.registerCallback(projectionCallback, mainHandler)
        workerThread = HandlerThread(WORKER_NAME).apply { start() }
        workerHandler = Handler(workerThread!!.looper)
        lastFrameAtMs = 0L
        framesProcessed = 0L
        Log.i(TAG, "MediaProjection acquired")

        if (!createCapture(projection)) {
            stop()
            return false
        }
        return true
    }

    private fun createCapture(projection: MediaProjection): Boolean {
        val size = screenSize()
        if (size.x <= 0 || size.y <= 0) return false
        val densityDpi = context.resources.displayMetrics.densityDpi
        val reader = try {
            ImageReader.newInstance(size.x, size.y, PixelFormat.RGBA_8888, IMAGE_BUFFER_COUNT)
        } catch (error: RuntimeException) {
            Log.e(TAG, "Unable to create ImageReader", error)
            return false
        }
        if (!reader.surface.isValid) {
            reader.close()
            return false
        }
        reader.setOnImageAvailableListener({ onImageAvailable(it) }, workerHandler)
        val display = try {
            projection.createVirtualDisplay(
                "CarromVisionCapture", size.x, size.y, densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface, null, workerHandler
            )
        } catch (error: RuntimeException) {
            Log.e(TAG, "Unable to create virtual display", error)
            reader.close()
            return false
        }
        if (display == null) {
            reader.close()
            return false
        }
        imageReader = reader
        virtualDisplay = display
        running = true
        Log.i(TAG, "Virtual display active ${size.x}x${size.y} @ ${densityDpi}dpi")
        return true
    }

    private fun onImageAvailable(reader: ImageReader) {
        val image = try {
            reader.acquireLatestImage()
        } catch (error: RuntimeException) {
            Log.w(TAG, "Unable to acquire frame", error)
            null
        } ?: return
        try {
            val now = SystemClock.elapsedRealtime()
            if (!running || now - lastFrameAtMs < FRAME_INTERVAL_MS) return
            lastFrameAtMs = now
            val detection = try {
                frameProcessor.detect(image)
            } catch (error: Exception) {
                Log.e(TAG, "Frame processing failed", error)
                null
            }
            if (detection == null) {
                // Do NOT clear overlay - keep board projection active and ready
                return
            }
            // Always keep overlay active if match is detected, regardless of trajectory
            framesProcessed++
            if (framesProcessed == 1L) {
                Log.i(TAG, "First live frame processed; aim pipeline active")
            } else if (framesProcessed % FRAME_LOG_INTERVAL == 0L) {
                Log.d(TAG, "Live frames processed: $framesProcessed")
            }
            val primary = detection.trajectory
            val assists = detection.assists
            overlayManager.update(
                primary, assists,
                detection.boardRect, frameProcessor.logicalBoardSize
            )
        } finally {
            image.close()
        }
    }

    private fun screenSize(): Point {
        val windowManager = context.getSystemService(WindowManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.maximumWindowMetrics.bounds
            return Point(bounds.width(), bounds.height())
        }
        val point = Point()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealSize(point)
        return point
    }

    fun stop() {
        if (!running && workerThread == null && imageReader == null && mediaProjection == null) {
            return
        }
        running = false
        Log.i(TAG, "Stopping capture pipeline")
        try {
            imageReader?.setOnImageAvailableListener(null, null)
        } catch (error: RuntimeException) {
            Log.w(TAG, "Unable to detach frame listener", error)
        }
        try {
            virtualDisplay?.release()
        } catch (error: RuntimeException) {
            Log.w(TAG, "Unable to release virtual display", error)
        }
        try {
            imageReader?.close()
        } catch (error: RuntimeException) {
            Log.w(TAG, "Unable to close ImageReader", error)
        }
        virtualDisplay = null
        imageReader = null
        mediaProjection?.let { projection ->
            try {
                projection.unregisterCallback(projectionCallback)
                projection.stop()
            } catch (error: RuntimeException) {
                Log.w(TAG, "Unable to stop MediaProjection", error)
            }
        }
        mediaProjection = null
        val handler = workerHandler
        val closePosted = handler != null && try {
            handler.post { frameProcessor.close() }
            true
        } catch (error: RuntimeException) {
            Log.w(TAG, "Unable to schedule buffer release", error)
            false
        }
        if (!closePosted) frameProcessor.close()
        workerThread?.quitSafely()
        workerThread = null
        workerHandler = null
    }

    private companion object {
        const val TAG = "VisionCaptureEngine"
        const val WORKER_NAME = "VisionWorkerThread"
        const val IMAGE_BUFFER_COUNT = 2
        const val FRAME_INTERVAL_MS = 33L
        const val FRAME_LOG_INTERVAL = 300L
    }
}
