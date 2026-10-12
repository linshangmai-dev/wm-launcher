// SPDX-License-Identifier: GPL-3.0-only
package com.dashline.launcher

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import org.json.JSONObject

/** Finite startup repairs; no resident root service or background polling. */
internal object WmHomeIntegration {
    const val CARD_ENABLED = "wm_weike_entry_enabled"
    private val lock = Any()
    private val automaticLock = Any()
    private var automaticBusy = false
    private var lastAutomaticFailed = false
    private var lastAutomaticCheck = -60_000L

    fun ensureOnStart(context: Context) {
        if (Build.VERSION.SDK_INT != 23) return
        // This OEM integration is inapplicable on ordinary Android devices/emulators.
        try { context.packageManager.getApplicationInfo("com.wm.launcher", 0) }
        catch (_: android.content.pm.PackageManager.NameNotFoundException) { return }
        val now = SystemClock.elapsedRealtime()
        synchronized(automaticLock) {
            if (automaticBusy || (lastAutomaticFailed && now - lastAutomaticCheck < 60_000L)) return
            automaticBusy = true
            lastAutomaticCheck = now
        }
        run(context, "ensure-on") { result ->
            synchronized(automaticLock) {
                automaticBusy = false
                lastAutomaticFailed = result.getOrNull()?.optBoolean("complete") != true
            }
        }
    }
    fun run(context: Context, action: String, callback: (Result<JSONObject>) -> Unit) {
        val app = context.applicationContext
        Thread({
            val result = synchronized(lock) { runCatching { perform(app, action) } }
            result.onSuccess { value ->
                if (value.has("cardEnabled")) app.getSharedPreferences("radio_launcher", Context.MODE_PRIVATE)
                    .edit().putBoolean(CARD_ENABLED, value.getBoolean("cardEnabled")).apply()
            }
            Handler(Looper.getMainLooper()).post { callback(result) }
        }, "WM-HomeSettings").start()
    }
    private fun perform(context: Context, action: String): JSONObject {
        check(Build.VERSION.SDK_INT == 23) { "此功能仅适配威马 Android 6.0.1 系统" }
        require(action in setOf("status", "ensure-on", "card-on", "card-off", "home-on", "home-off"))
        // Create the parent under our normal UID, before a root helper creates a backup child.
        check(context.filesDir.isDirectory) { "无法打开桌面的备份目录" }
        val command = "CLASSPATH=${quote(context.applicationInfo.sourceDir)} /system/bin/app_process /system/bin " +
            "com.dashline.launcher.WmSystemActions ${quote(action)} ${quote(context.packageName)}"
        // Select a working transport using read-only probes. No mutation is retried.
        val su = WmRootShell.find()
        val output = if (su != null) su.execute(command, 20_000) else {
            WmAdbRecovery.connect(context).use { adb ->
                check(adb.shell("id").contains("uid=0(")) { "当前调试服务没有系统修改权限" }
                adb.shell(command)
            }
        }
        val line = output.lineSequence().lastOrNull { it.startsWith(WmSystemActions.PREFIX) }
            ?: error("没有收到修改结果，请重新检查开关状态。${output.takeLast(240)}")
        val result = JSONObject(line.removePrefix(WmSystemActions.PREFIX))
        check(result.optBoolean("ok")) { result.optString("message", "修改没有完成") }
        return result
    }
    private fun quote(value: String) = "'" + value.replace("'", "'\\''") + "'"
}
