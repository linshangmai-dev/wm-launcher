// SPDX-License-Identifier: GPL-3.0-only
package com.dashline.launcher

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import androidx.appcompat.widget.AppCompatTextView

/** Keep the large clock size on the car; shrink only if the column is too narrow. */
class DashboardClockView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = android.R.attr.textViewStyle
) : AppCompatTextView(context, attrs, defStyleAttr) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val preferred = resources.getDimension(R.dimen.clock_text)
        val available = MeasureSpec.getSize(widthMeasureSpec) - compoundPaddingLeft - compoundPaddingRight
        if (MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED && available > 0) {
            val measurement = android.text.TextPaint(paint).apply { textSize = preferred }
            val required = measurement.measureText(text.toString())
            val minimum = 28f * resources.displayMetrics.scaledDensity
            val fitted = if (required > available) preferred * available / required else preferred
            val size = fitted.coerceIn(minimum, preferred)
            if (kotlin.math.abs(textSize - size) > 0.1f) setTextSize(TypedValue.COMPLEX_UNIT_PX, size)
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
}
