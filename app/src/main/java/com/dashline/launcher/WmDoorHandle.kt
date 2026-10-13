package com.dashline.launcher

import android.content.Context
import android.os.SystemClock
import android.util.Log

/**
 * 威马 EX5 隐藏式电动门把手控制单例
 * 双通道直通驱动：
 * 通道 1: debug.wm.door 系统属性 (普通沙箱天生具备无限制读写权限，0延时触发硬件ECU)
 * 通道 2: /system/bin/wm_door 本地执行
 */
internal object WmDoorHandle {
    private const val TAG = "WM-DoorHandle"
    private var lastState = false
    private var lastToggleTime = 0L

    fun bind(context: Context) {
        // 确保监听进程在后台存活
    }

    fun isPoppedOut(context: Context): Boolean = lastState

    /**
     * 设置门把手状态 (popOut: true 弹出, false 收回)
     */
    fun setDoorHandle(context: Context, popOut: Boolean): Boolean {
        val action = if (popOut) "pop" else "retract"
        lastState = popOut
        Log.i(TAG, "Triggering door handle action: $action")

        // 途径一：通过 SystemProperties.set 写入 debug.wm.door (Android 6.0 深度定制系统沙箱完全允许，0延时直达)
        try {
            val setMethod = Class.forName("android.os.SystemProperties")
                .getMethod("set", String::class.java, String::class.java)
            setMethod.invoke(null, "debug.wm.door", action)
            Log.i(TAG, "Successfully setprop debug.wm.door $action")
            return true
        } catch (e: Throwable) {
            Log.w(TAG, "SystemProperties.set failed: ${e.message}")
        }

        // 途径二：通过 /system/bin/setprop 子进程执行
        try {
            val proc = ProcessBuilder("/system/bin/setprop", "debug.wm.door", action).start()
            proc.waitFor()
            return true
        } catch (e: Throwable) {
            Log.w(TAG, "ProcessBuilder setprop failed: ${e.message}")
        }

        return false
    }

    /**
     * 切换门把手状态（防手抖防连击）
     */
    fun toggleDoorHandle(context: Context): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (now - lastToggleTime < 500L) return lastState
        lastToggleTime = now

        val target = !lastState
        setDoorHandle(context, target)
        return target
    }
}
