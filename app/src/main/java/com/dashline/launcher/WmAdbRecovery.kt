// SPDX-License-Identifier: GPL-3.0-only
package com.dashline.launcher

import android.content.Context
import java.io.IOException
import java.lang.reflect.InvocationTargetException

/** Recover a blocked engineering adbd before opening a command stream, at most once. */
internal object WmAdbRecovery {
    fun connect(context: Context): WmLocalAdb {
        fun attempt() = WmLocalAdb(context).also { it.connect() }
        val first = try { return attempt() } catch (failure: WmAdbHandshakeException) { failure }
        if (!first.mayRecover || property("init.svc.adbd") != "running" ||
            property("service.adb.tcp.port") != "5555" || property("ro.adb.secure") != "0") throw first

        // This car's engineering daemon blocks a second TCP host while the PC stays
        // connected. The existing SELinux-disabled firmware allows this property.
        // Do not enable a stopped daemon or interrupt a pending AUTH prompt.
        try {
            Class.forName("android.os.SystemProperties").getMethod("set", String::class.java, String::class.java)
                .invoke(null, "ctl.restart", "adbd")
        } catch (failure: Exception) {
            val cause = if (failure is InvocationTargetException) failure.targetException else failure
            throw IOException("ADB 已开启，但本地调试连接被占用，无法自动恢复。请关闭再打开 ADB 开关后重试。", cause)
        }
        // ctl.restart is asynchronous; connecting to the old listener would reset
        // the socket as init stops it. All retries below are handshake-only.
        Thread.sleep(400)
        var last: Exception = first
        repeat(3) {
            try { return attempt() } catch (failure: WmAdbHandshakeException) {
                if (!failure.mayRecover && failure.cause !is java.net.ConnectException) throw failure
                last = failure
            }
            Thread.sleep(100)
        }
        throw IOException("ADB 已开启，但本地调试服务仍未回应。请关闭再打开 ADB 开关后重试。", last)
    }

    private fun property(name: String): String = runCatching {
        Class.forName("android.os.SystemProperties").getMethod("get", String::class.java)
            .invoke(null, name) as String
    }.getOrDefault("")
}
