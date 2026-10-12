// SPDX-License-Identifier: GPL-3.0-only
package com.dashline.launcher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View

/** Registration indicator. Fixed antenna deliberately does not imply signal bars. */
class WmLteView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var status = WmLteStatus()
    internal fun render(value: WmLteStatus) {
        status = value
        contentDescription = "车联网 ${value.accessText}，${value.registrationText}，点击查看连接详情"
        invalidate()
    }
    init {
        isClickable = true; isFocusable = true
        render(status)
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size = minOf(width / 3.0f, height / 4.0f)
        val center = width / 2f
        val top = height / 2f - size
        paint.color = if (status.connected) 0xFF20C878.toInt() else 0xFF8093A5.toInt()
        paint.strokeWidth = maxOf(2f, size / 9f)
        paint.style = Paint.Style.STROKE
        canvas.drawLine(center, top, center, top + size, paint)
        val path = Path().apply { moveTo(center - size / 2, top + size); lineTo(center, top); lineTo(center + size / 2, top + size) }
        canvas.drawPath(path, paint)
        canvas.drawArc(center - size, top - size * 0.4f, center + size, top + size * 0.9f, 200f, 140f, false, paint)
        paint.style = Paint.Style.FILL
        paint.textSize = minOf(width * 0.34f, height * 0.3f)
        paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
        paint.textAlign = Paint.Align.CENTER
        val label = when { status.registration == null -> "LTE ?"; !status.connected -> "LTE ×"; status.access == 6 -> "LTE"; else -> status.accessText }
        canvas.drawText(label, center, height * 0.87f, paint)
    }
}
