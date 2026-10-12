// SPDX-License-Identifier: GPL-3.0-only
package com.dashline.launcher

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import androidx.appcompat.widget.AppCompatTextView

/** Preserve the large car font and fit the complete value on a narrower screen. */
class VehicleStatView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = android.R.attr.textViewStyle
) : AppCompatTextView(context, attrs, defStyleAttr) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val preferred = resources.getDimension(if (tag == "trip") R.dimen.trip_stat_value
            else R.dimen.vehicle_stat_value)
        val available = MeasureSpec.getSize(widthMeasureSpec) - compoundPaddingLeft - compoundPaddingRight
        if (MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED && available > 0) {
            val measurement = android.text.TextPaint(paint).apply { textSize = preferred }
            val required = measurement.measureText(text.toString())
            val fitted = if (required > available) preferred * available / required else preferred
            if (kotlin.math.abs(textSize - fitted) > 0.1f) setTextSize(TypedValue.COMPLEX_UNIT_PX, fitted)
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
}
