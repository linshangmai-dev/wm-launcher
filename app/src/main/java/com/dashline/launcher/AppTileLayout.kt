// SPDX-License-Identifier: GPL-3.0-only
package com.dashline.launcher

import android.content.Context
import android.util.AttributeSet
import android.widget.ImageView
import android.widget.LinearLayout

/** Keep large icons inside their column on narrow screens and split panels. */
class AppTileLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val icon = findViewById<ImageView>(R.id.appIcon)
            ?: findViewById<ImageView>(R.id.factoryIcon)
        if (icon != null) {
            val desired = resources.getDimensionPixelSize(R.dimen.app_icon_size)
            val available = (MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight)
                .coerceAtLeast(1)
            val size = if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED)
                desired else minOf(desired, available)
            icon.layoutParams.width = size
            icon.layoutParams.height = size
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
}
