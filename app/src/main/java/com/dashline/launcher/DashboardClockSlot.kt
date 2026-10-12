// SPDX-License-Identifier: GPL-3.0-only
package com.dashline.launcher

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs

/** Move the clock as a group without reducing its font-fitting width. */
class DashboardClockSlot @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        var room = Float.POSITIVE_INFINITY
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            if (child.visibility != View.VISIBLE) continue
            val drawnLeft = if (child is TextView) {
                if (child.text.isEmpty()) continue
                val textLayout = child.layout ?: continue
                child.left + child.compoundPaddingLeft +
                    (0 until textLayout.lineCount).minOf { textLayout.getLineLeft(it) }
            } else child.left.toFloat()
            room = minOf(room, drawnLeft)
        }
        // A narrow screen may have no spare space; keep text inside its column.
        val shift = if (room.isFinite()) minOf(resources.getDimension(R.dimen.clock_left_shift),
            room.coerceAtLeast(0f)) else 0f
        if (abs(translationX + shift) > 0.1f) translationX = -shift
    }
}
