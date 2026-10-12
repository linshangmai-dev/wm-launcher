// SPDX-License-Identifier: GPL-3.0-only
package com.dashline.launcher

import android.content.Context
import android.text.TextPaint
import android.util.AttributeSet
import android.util.TypedValue
import androidx.appcompat.widget.AppCompatTextView
import kotlin.math.abs

/** Keep each range label beside its value; stack complete entries on narrow screens. */
class VehicleRangeView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = android.R.attr.textViewStyle
) : AppCompatTextView(context, attrs, defStyleAttr) {
    private var factoryRange = context.getString(R.string.wm_vehicle_range_unknown)
    private var actualRange = context.getString(R.string.wm_actual_estimated_range_unknown)

    fun setRanges(factory: String, actual: String) {
        val nextFactory = context.getString(R.string.wm_vehicle_range_inline, factory)
        val nextActual = context.getString(R.string.wm_actual_estimated_range_inline, actual)
        if (factoryRange == nextFactory && actualRange == nextActual) return
        factoryRange = nextFactory
        actualRange = nextActual
        contentDescription = "$factoryRange，$actualRange"
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val preferred = resources.getDimension(R.dimen.vehicle_stat_value)
        val measurement = TextPaint(paint).apply { textSize = preferred }
        val combined = "$factoryRange    $actualRange"
        val available = MeasureSpec.getSize(widthMeasureSpec) - compoundPaddingLeft - compoundPaddingRight
        val bounded = MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED && available > 0
        // Avoid squeezing both entries into an unreadably small single line.
        val oneLine = !bounded || measurement.measureText(combined) * 0.8f <= available
        val display = if (oneLine) combined else "$factoryRange\n$actualRange"
        val required = display.split('\n').maxOf { measurement.measureText(it) }
        val fitted = if (bounded && required > available) preferred * available / required else preferred
        val lines = if (oneLine) 1 else 2
        if (maxLines != lines) maxLines = lines
        if (text.toString() != display) text = display
        if (abs(textSize - fitted) > 0.1f) setTextSize(TypedValue.COMPLEX_UNIT_PX, fitted)
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
}
