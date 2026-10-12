// SPDX-License-Identifier: GPL-3.0-only
package com.dashline.launcher

import android.app.Instrumentation
import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageView
import java.io.File

/** Fixed comparison data on an emulator only; excluded from every release APK. */
class WmVisualRuntime : Instrumentation() {
    private var args = Bundle()
    override fun onCreate(arguments: Bundle?) { args = arguments ?: Bundle(); start() }
    override fun onStart() {
        try {
            require(Build.VERSION.SDK_INT == 23 && Build.FINGERPRINT.contains("generic"))
            val context = targetContext
            require(context.packageName == "com.wmhome.launcher")
            context.getSharedPreferences("radio_launcher", 0).edit()
                .putBoolean("setup_done", true).putBoolean("asked_location", true)
                .putString("language", "zh").putString("gradient", "wm_ice")
                .putString("theme_mode", "dark").putBoolean("wm_visual_v17", true)
                .putString("wm_orientation", args.getString("orientation", "portrait"))
                .putString("card_second_mode", Prefs.CARD_SHORTCUTS).commit()
            val activity = startActivitySync(Intent(Intent.ACTION_MAIN).setComponent(
                ComponentName(context.packageName, "com.dashline.launcher.HomeActivity"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as HomeActivity
            waitForIdleSync()
            val playing = args.getString("state", "idle") == "playing"
            var comparisonApps = emptyList<AppInfo>()
            // Production listeners are stopped to freeze the exact comparison state.
            runOnMainSync {
                fun field(name: String) = HomeActivity::class.java.getDeclaredField(name).apply { isAccessible = true }
                (field("handler").get(activity) as Handler).removeCallbacksAndMessages(null)
                (field("tirePressureMonitor").get(activity) as? WmTirePressureMonitor)?.stop()
                (field("lteMonitor").get(activity) as? WmForegroundPoll<*>)?.stop()
                (field("mediaMonitor").get(activity) as? MediaMonitor)?.stop()
                fun text(id: Int, value: String) { activity.findViewById<TextView>(id).text = value }
                text(R.id.batteryValue, "93%")
                activity.findViewById<VehicleRangeView>(R.id.rangeValue).setRanges("374 km", "260.4 km")
                text(R.id.frontLeft, "270"); text(R.id.frontRight, "275")
                text(R.id.rearLeft, "270"); text(R.id.rearRight, "286")
                activity.findViewById<View>(R.id.pressureStatus).visibility = View.GONE
                text(R.id.tripAValue, "6.5 km"); text(R.id.tripBValue, "771.9 km")
                text(R.id.totalValue, "67394 km"); text(R.id.bigClock, "16:20")
                text(R.id.bigDate, "10月12日 星期一")
                text(R.id.mediaTitle, if (playing) "Burning" else "暂无正在播放的音乐")
                text(R.id.mediaArtist, if (playing) "Maria Arredondo · Not Going Under" else "点按打开 Player")
                activity.findViewById<View>(R.id.mediaSeekRow).visibility = if (playing) View.VISIBLE else View.GONE
                text(R.id.mediaElapsed, "1:56"); text(R.id.mediaTotal, "4:02")
                val shortcuts = activity.findViewById<LinearLayout>(R.id.panelShortcuts)
                val factory = listOf("车辆控制" to R.drawable.wm_ic_vehicle,
                    "能量管理" to R.drawable.wm_ic_energy, "全景环视" to R.drawable.wm_ic_surround)
                AppGrid.shortcuts(shortcuts, 3) { index, parent ->
                    val item = com.dashline.launcher.databinding.ItemAppBinding.inflate(activity.layoutInflater, parent, false)
                    item.root.minimumHeight = 0
                    item.appLabel.minLines = 1
                    item.appLabel.setTypeface(null, android.graphics.Typeface.BOLD)
                    item.appIcon.setImageResource(factory[index].second)
                    item.appIcon.setColorFilter(android.graphics.Color.parseColor("#49A7F5"))
                    item.appLabel.text = factory[index].first
                    item.root
                }
                val names = listOf("ADB开关", "Alook", "DiPlay 威马测试版", "LANShare", "哔哩哔哩",
                    "抖音短视频", "旋转控制", "氢桌面", "甲壳虫ADB助手")
                val apps = names.mapIndexed { index, name ->
                    val file = File(context.filesDir, "wm17-icons/$index.png")
                    require(file.isFile)
                    AppInfo(name, "com.wmhome.visual.fixture$index", BitmapDrawable(activity.resources, file.path))
                }
                comparisonApps = apps
                // Keep a pager present, as on the car. Use the production row
                // count on the first layout rather than forcing three rows.
                val rows = field("drawerRows").getInt(activity).coerceAtLeast(2)
                field("drawerPages").set(activity, apps.chunked(rows * 3) + listOf(apps.take(3)))
                field("drawerPage").setInt(activity, 0)
                HomeActivity::class.java.getDeclaredMethod("renderDrawerPage").apply { isAccessible = true }.invoke(activity)
            }
            waitForIdleSync()
            // Let the renderer complete one frame after the comparison data is bound.
            android.os.SystemClock.sleep(250)
            var spacingResult = "landscape keeps its separate application drawer"
            if (args.getString("orientation", "portrait") == "portrait") {
                runOnMainSync {
                    fun field(name: String) = HomeActivity::class.java.getDeclaredField(name).apply { isAccessible = true }
                    val grid = activity.findViewById<LinearLayout>(R.id.portraitDrawerGrid)
                    val rows = HomeActivity::class.java.getDeclaredMethod("rowsThatFit", View::class.java)
                        .apply { isAccessible = true }.invoke(activity, grid) as Int
                    require(rows == 3) { "Production layout fits only $rows rows in $playing state" }
                    field("drawerRows").setInt(activity, rows)
                    field("drawerPages").set(activity, listOf(comparisonApps, comparisonApps.take(3)))
                    HomeActivity::class.java.getDeclaredMethod("renderDrawerPage")
                        .apply { isAccessible = true }.invoke(activity)
                }
                waitForIdleSync()
                android.os.SystemClock.sleep(250)
                runOnMainSync {
                    val grid = activity.findViewById<LinearLayout>(R.id.portraitDrawerGrid)
                    val bar = activity.findViewById<View>(R.id.tabBar)
                    require(grid.childCount == 3)
                    val position = IntArray(2); grid.getLocationInWindow(position)
                    val barPosition = IntArray(2); bar.getLocationInWindow(barPosition)
                    val third = grid.getChildAt(2) as LinearLayout
                    require(position[1] + third.bottom + 50 <= barPosition[1])
                    for (rowIndex in 0..2) {
                        val row = grid.getChildAt(rowIndex) as LinearLayout
                        for (column in 0..2) {
                            val tile = row.getChildAt(column)
                            val icon = tile.findViewById<View>(R.id.appIcon)
                            val label = tile.findViewById<TextView>(R.id.appLabel)
                            require(icon.height == 128 && label.textSize == 29f)
                            require(label.bottom <= tile.height - tile.paddingBottom)
                        }
                    }
                    spacingResult = "state=${if (playing) "playing" else "idle"}, rows=3, rowHeight=${third.height}, gridTop=${position[1]}, thirdRowBottom=${position[1] + third.bottom}, tabTop=${barPosition[1]}, all nine icons and labels fit"
                }
            }
            val bitmap = uiAutomation.takeScreenshot()
            File(context.filesDir, "wm18-${args.getString("orientation", "portrait")}-${args.getString("state", "idle")}.png").outputStream().use {
                require(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
            }
            finish(0, Bundle().apply { putString("stream", "WM visual fixture ready on emulator API 23; $spacingResult\n") })
        } catch (error: Throwable) {
            finish(1, Bundle().apply { putString("stream", error.stackTraceToString()) })
        }
    }
}
