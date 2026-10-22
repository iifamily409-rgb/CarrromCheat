package com.example.carromvision.overlay

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import java.util.Locale
import kotlin.math.roundToInt

/** Translucent live-configuration sheet shown inside the overlay window. */
internal fun buildSettingsPanel(
    context: Context,
    initial: OverlayConfig,
    onConfigChanged: (OverlayConfig) -> Unit,
    onClose: () -> Unit
): View {
    val density = context.resources.displayMetrics.density
    var current = initial
    fun emit(updated: OverlayConfig) {
        current = updated
        onConfigChanged(updated)
    }

    val panel = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(density, 18), dp(density, 16), dp(density, 18), dp(density, 16))
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(density, 20f).toFloat()
            setColor(Color.argb(238, 14, 18, 25))
            setStroke(dp(density, 1.4f), Color.argb(180, 100, 220, 255))
        }
    }

    panel.addView(text(context, "CARROMVISION AIM", 17f, Color.WHITE))
    panel.addView(text(context, "Drag the badge • tap to open this panel", 11f, 0xFF8FA8B8.toInt()))
    panel.addView(text(context, "LINE GLOW COLOR", 10f, 0xFF54E1FF.toInt()))

    val colorRow = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    val palette = OverlayPalette.lineChoices
    val chips = ArrayList<Pair<View, Int>>()
    fun chipBackground(color: Int, selected: Boolean): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            setStroke(
                dp(density, if (selected) 3f else 1f),
                if (selected) Color.WHITE else Color.argb(120, 255, 255, 255)
            )
        }
    fun refreshChips() {
        for ((view, color) in chips) {
            view.background = chipBackground(color, color == current.lineColor)
        }
    }
    for (color in palette) {
        val chip = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(density, 28), dp(density, 28)).apply {
                marginEnd = dp(density, 8)
            }
            contentDescription = "Line color"
            setOnClickListener {
                emit(current.copy(lineColor = color))
                refreshChips()
            }
        }
        chips.add(chip to color)
        colorRow.addView(chip)
    }
    refreshChips()
    panel.addView(colorRow)
    panel.addView(
        text(context, "Assist rays cycle cyan • gold • red • green • purple", 10f, 0xFF8FA8B8.toInt())
    )

    addSlider(panel, context, "Glow width", 0.6f, 2.2f, initial.glowThickness) {
        emit(current.copy(glowThickness = it))
    }
    addSlider(panel, context, "Puck radius", 0.75f, 1.35f, initial.puckRadiusMultiplier) {
        emit(current.copy(puckRadiusMultiplier = it))
    }
    addSlider(panel, context, "Striker radius", 0.75f, 1.35f, initial.strikerRadiusMultiplier) {
        emit(current.copy(strikerRadiusMultiplier = it))
    }

    addToggle(panel, context, "Multi-puck lines", initial.multiLineEnabled) {
        emit(current.copy(multiLineEnabled = it))
    }
    addToggle(panel, context, "Cushion bank lines", initial.bankLinesEnabled) {
        emit(current.copy(bankLinesEnabled = it))
    }

    panel.addView(styledButton(context, "CLOSE PANEL").apply {
        setOnClickListener { onClose() }
    })

    return ScrollView(context).apply {
        isFillViewport = false
        addView(panel, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }
}

private fun addSlider(
    parent: LinearLayout,
    context: Context,
    label: String,
    min: Float,
    max: Float,
    value: Float,
    onChange: (Float) -> Unit
) {
    val density = context.resources.displayMetrics.density
    val caption = text(context, "", 11f, Color.argb(220, 255, 255, 255))
    val bar = SeekBar(context).apply {
        this.max = 100
        progress = ((value - min) / (max - min) * 100f).roundToInt().coerceIn(0, 100)
        contentDescription = label
    }
    fun update(value: Float) {
        caption.text = "$label: ${String.format(Locale.US, "%.2fx", value)}"
    }
    update(value)
    bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
            val mapped = min + (max - min) * (progress / 100f)
            update(mapped)
            onChange(mapped)
        }
        override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
        override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
    })
    parent.addView(caption)
    parent.addView(
        bar,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(density, 34)
        )
    )
}

private fun addToggle(
    parent: LinearLayout,
    context: Context,
    label: String,
    initial: Boolean,
    onChange: (Boolean) -> Unit
) {
    var enabled = initial
    val button = styledButton(context, "").apply {
        text = "$label: ${if (enabled) "ON" else "OFF"}"
        setOnClickListener {
            enabled = !enabled
            text = "$label: ${if (enabled) "ON" else "OFF"}"
            onChange(enabled)
        }
    }
    parent.addView(button)
}

private fun styledButton(context: Context, label: String): Button =
    Button(context).apply {
        text = label
        isAllCaps = false
        textSize = 12f
        setTextColor(Color.WHITE)
        backgroundTintList = ColorStateList.valueOf(Color.argb(55, 255, 255, 255))
        minHeight = dp(resources.displayMetrics.density, 40)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(resources.displayMetrics.density, 6) }
    }

private fun text(context: Context, value: String, size: Float, color: Int): TextView =
    TextView(context).apply {
        text = value
        textSize = size
        setTextColor(color)
        setPadding(0, dp(resources.displayMetrics.density, 6), 0, dp(resources.displayMetrics.density, 2))
    }

private fun dp(density: Float, value: Number): Int =
    (value.toFloat() * density).roundToInt()
