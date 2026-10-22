package com.example.carromvision.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import kotlin.math.abs
import kotlin.math.roundToInt

/** Owns the draggable bubble and its live translucent configuration sheet. */
class FloatingMenuManager(
    private val context: Context,
    private val onConfigChanged: (OverlayConfig) -> Unit
) {
    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val density = context.resources.displayMetrics.density
    private var bubbleView: FloatingBubbleView? = null
    private var settingsView: View? = null
    private var config = OverlayConfig()
    private var isSettingsOpen = false

    @SuppressLint("ClickableViewAccessibility")
    fun show() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            Handler(Looper.getMainLooper()).post { show() }
            return
        }
        if (bubbleView != null) return

        val size = dp(58)
        val bubble = FloatingBubbleView(context).apply {
            contentDescription = "Open CarromVision settings"
            setOnClickListener { toggleSettings() }
        }
        val screenWidth = context.resources.displayMetrics.widthPixels
        val params = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (screenWidth - size - dp(16)).coerceAtLeast(dp(8))
            y = dp(96)
        }

        val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var dragging = false
        bubble.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    dragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - initialTouchX
                    val deltaY = event.rawY - initialTouchY
                    if (abs(deltaX) > touchSlop || abs(deltaY) > touchSlop) {
                        dragging = true
                    }
                    if (dragging) {
                        val maxX = context.resources.displayMetrics.widthPixels - size - dp(4)
                        val maxY = context.resources.displayMetrics.heightPixels - size - dp(4)
                        params.x = (initialX + deltaX).roundToInt().coerceIn(dp(4), maxX)
                        params.y = (initialY + deltaY).roundToInt().coerceIn(dp(4), maxY)
                        try {
                            windowManager.updateViewLayout(view, params)
                        } catch (error: RuntimeException) {
                            Log.w(TAG, "Unable to move bubble", error)
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!dragging) view.performClick()
                    true
                }
                MotionEvent.ACTION_CANCEL -> true
                else -> false
            }
        }

        try {
            windowManager.addView(bubble, params)
            bubbleView = bubble
        } catch (error: RuntimeException) {
            Log.e(TAG, "Unable to add floating bubble", error)
        }
    }

    private fun toggleSettings() {
        if (isSettingsOpen) {
            removeSettings()
            return
        }

        val screenWidth = context.resources.displayMetrics.widthPixels
        val screenHeight = context.resources.displayMetrics.heightPixels
        val panelWidth = minOf(dp(318), screenWidth - dp(24)).coerceAtLeast(dp(240))
        val panelHeight = minOf(dp(640), (screenHeight * 0.80f).toInt())
            .coerceAtLeast(dp(320))
        val panel = buildSettingsPanel(
            context = context,
            initial = config,
            onConfigChanged = { updated ->
                config = updated
                onConfigChanged(updated)
            },
            onClose = { toggleSettings() }
        )
        val params = WindowManager.LayoutParams(
            panelWidth, panelHeight,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(12)
            y = dp(164)
        }

        try {
            windowManager.addView(panel, params)
            settingsView = panel
            isSettingsOpen = true
        } catch (error: RuntimeException) {
            Log.e(TAG, "Unable to add settings panel", error)
            settingsView = null
            isSettingsOpen = false
        }
    }

    private fun removeSettings() {
        settingsView?.let {
            try {
                windowManager.removeView(it)
            } catch (error: RuntimeException) {
                Log.w(TAG, "Unable to remove settings panel", error)
            }
        }
        settingsView = null
        isSettingsOpen = false
    }

    fun destroy() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            Handler(Looper.getMainLooper()).post { destroy() }
            return
        }
        bubbleView?.let {
            try {
                windowManager.removeView(it)
            } catch (error: RuntimeException) {
                Log.w(TAG, "Unable to remove floating bubble", error)
            }
        }
        removeSettings()
        bubbleView = null
    }

    private fun dp(value: Int): Int = (value * density).roundToInt()

    private companion object {
        const val TAG = "FloatingMenuManager"
    }
}
