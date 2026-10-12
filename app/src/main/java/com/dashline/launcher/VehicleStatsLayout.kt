// SPDX-License-Identifier: GPL-3.0-only
package com.dashline.launcher

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout

/** Use physical pixels for the requested inset; keep narrow screens readable. */
class VehicleStatsLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val edge = (8 * resources.displayMetrics.density).toInt()
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val battery = resources.getDimensionPixelSize(R.dimen.vehicle_battery_width)
        val gap = resources.getDimensionPixelSize(R.dimen.vehicle_range_gap)
        val minRange = resources.getDimensionPixelSize(R.dimen.vehicle_range_min_width)
        val previousInset = minOf(150, (width - edge * 2 - battery - gap - minRange).coerceAtLeast(0))
        val shiftLeft = minOf(80, previousInset)
        val start = edge + previousInset - shiftLeft
        val end = edge + shiftLeft
        // Transfer the same physical pixels to the end padding so both ranges
        // move with the battery without changing their width or fitted font size.
        if (paddingStart != start || paddingEnd != end) {
            setPaddingRelative(start, paddingTop, end, paddingBottom)
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
}
