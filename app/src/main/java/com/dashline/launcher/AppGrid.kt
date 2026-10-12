// SPDX-License-Identifier: GPL-3.0-only
package com.dashline.launcher

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout

/** Shared three-column layout for the dashboard and its shortcut editor. */
object AppGrid {
    const val COLUMNS = 3

    fun tileHeight(context: Context, gridWidth: Int): Int {
        return measuredTileHeight(context, gridWidth) + context.resources.getDimensionPixelSize(R.dimen.app_row_extra)
    }

    /** Tighten only the home's empty row spacing so three complete tiles fit. */
    fun homeTileHeight(context: Context, gridWidth: Int, availableHeight: Int = 0): Int {
        val tile = measuredTileHeight(context, gridWidth)
        val preferredGap = context.resources.getDimensionPixelSize(R.dimen.home_app_row_extra)
        val gap = if (availableHeight > 0) minOf(preferredGap,
            (availableHeight / 3 - tile).coerceAtLeast(0)) else preferredGap
        return tile + gap
    }

    private fun measuredTileHeight(context: Context, gridWidth: Int): Int {
        val tile = LayoutInflater.from(context).inflate(R.layout.item_app, null, false)
        val width = (gridWidth / COLUMNS).coerceAtLeast(1)
        tile.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        return tile.measuredHeight
    }

    /** One row of three shortcuts leaves the lower dashboard for applications. */
    fun shortcuts(container: LinearLayout, count: Int, bind: (Int, LinearLayout) -> View) {
        require(count in 0..COLUMNS)
        container.removeAllViews()
        container.orientation = LinearLayout.HORIZONTAL
        container.isBaselineAligned = false
        val context = container.context
        for (index in 0 until COLUMNS) {
            val cell = if (index < count) bind(index, container) else View(context)
            container.addView(cell, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
    }
}
