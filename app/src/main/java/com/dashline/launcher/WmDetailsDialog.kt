// SPDX-License-Identifier: GPL-3.0-only
package com.dashline.launcher

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat

/** A large, scrollable popup shared by the battery and connection entry points. */
internal class WmDetailsDialog(private val context: Context, title: String,
    private val read: () -> List<DetailRow>, private val closed: () -> Unit) {
    private val density = context.resources.displayMetrics.density
    private fun dp(value: Int) = (value * density + 0.5f).toInt()
    private val content = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val body = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(24), dp(20), dp(24), dp(12))
        setBackgroundColor(ContextCompat.getColor(context, R.color.bg))
    }
    private val status = text("正在读取…", 20f, false)
    private val scroll = ScrollView(context).apply {
        isFillViewport = false
        addView(content, ViewGroup.LayoutParams(-1, -2))
    }
    private val dialog: AlertDialog
    private val poll = WmForegroundPoll("WM-Details", 10000, {
        try { read() } catch (_: Exception) { listOf(DetailRow("数据", "暂时不可用")) }
    }) { rows -> render(rows) }
    private var signature: List<DetailRow>? = null

    init {
        body.addView(text(title, 30f, true))
        body.addView(status)
        body.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val close = Button(context).apply {
            text = "关闭"; textSize = 26f; minHeight = dp(64)
            setOnClickListener { dismiss() }
        }
        body.addView(close, LinearLayout.LayoutParams(-1, dp(64)))
        dialog = AlertDialog.Builder(context).setView(body).create()
        dialog.setOnDismissListener { poll.stop(); closed() }
    }

    private fun text(value: String, size: Float, bold: Boolean) = TextView(context).apply {
        text = value; textSize = size
        setTextColor(ContextCompat.getColor(context, R.color.text_primary))
        if (bold) setTypeface(typeface, Typeface.BOLD)
        setPadding(0, dp(5), 0, dp(5))
    }

    fun show() {
        dialog.show()
        val screen = context.resources.displayMetrics
        // Physical screen width is safer than assuming a car's density is sane.
        dialog.window?.setLayout((screen.widthPixels * 0.94f).toInt(), (screen.heightPixels * 0.86f).toInt())
        poll.start()
    }

    fun dismiss() { poll.stop(); dialog.dismiss() }

    private fun render(rows: List<DetailRow>) {
        status.text = "显示原车数据 · 每 10 秒刷新"
        if (signature == rows) return
        val oldScroll = scroll.scrollY
        signature = rows.toList()
        content.removeAllViews()
        val narrow = context.resources.configuration.screenWidthDp < 520
        for (row in rows) {
            if (row.heading) {
                content.addView(text(row.label, 27f, true).apply { setPadding(0, dp(20), 0, dp(8)) })
            } else {
                val pair = LinearLayout(context).apply {
                    orientation = if (narrow) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    minimumHeight = dp(58)
                    setPadding(0, dp(6), 0, dp(6))
                }
                pair.addView(text(row.label, 23f, false),
                    if (narrow) LinearLayout.LayoutParams(-1, -2) else LinearLayout.LayoutParams(0, -2, 0.42f))
                pair.addView(text(row.value, 25f, true).apply { textAlignment = View.TEXT_ALIGNMENT_VIEW_START },
                    if (narrow) LinearLayout.LayoutParams(-1, -2) else LinearLayout.LayoutParams(0, -2, 0.58f))
                content.addView(pair)
                content.addView(View(context).apply { setBackgroundColor(ContextCompat.getColor(context, R.color.divider)) },
                    LinearLayout.LayoutParams(-1, dp(1)))
            }
        }
        scroll.post { scroll.scrollTo(0, oldScroll) }
    }
}
