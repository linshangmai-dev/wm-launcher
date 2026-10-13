package com.dashline.launcher

import android.content.Context
import android.os.IBinder
import android.os.Parcel
import android.util.Log

/**
 * 威马 EX5 隐藏式电动门把手控制单例
 * 多重兜底保障控制必达：
 * 1. 原生执行 /system/bin/wm_door (系统提权命令，响应最快、无权限阻断)
 * 2. WmRootShell (/system/xbin/su 管道调用)
 * 3. 本地 ADB Loopback 回环调试通道
 * 4. 原生 Binder (yfcarcontrol / com.yftech.carmanager.ICarControl)
 */
internal object WmDoorHandle {
    private const val TAG = "WM-DoorHandle"
    private const val SERVICE_NAME = "yfcarcontrol"
    private const val SERVICE_DESCRIPTOR = "com.yftech.carmanager.ICarControl"
    private const val CODE_SET_POWER_HANDLE = 72
    private const val CODE_GET_POWER_HANDLE = 73

    private var lastState = false

    private fun getService(): IBinder? = try {
        Class.forName("android.os.ServiceManager")
            .getMethod("getService", String::class.java)
            .invoke(null, SERVICE_NAME) as? IBinder
    } catch (e: Throwable) {
        null
    }

    /**
     * 读取当前门把手状态
     * @return true 为已弹出，false 为已收回
     */
    fun isPoppedOut(): Boolean {
        // 尝试通过 Binder 读取
        val binder = getService()
        if (binder != null) {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.writeInterfaceToken(SERVICE_DESCRIPTOR)
                if (binder.transact(CODE_GET_POWER_HANDLE, data, reply, 0)) {
                    reply.readException()
                    val state = reply.readInt() != 0
                    lastState = state
                    return state
                }
            } catch (e: Throwable) {
                Log.w(TAG, "isPoppedOut binder failed: ${e.message}")
            } finally {
                reply.recycle()
                data.recycle()
            }
        }

        // 尝试通过 su 读取
        try {
            val root = WmRootShell.find()
            if (root != null) {
                val output = root.execute("service call yfcarcontrol 73", 1500)
                val state = output.contains("00000001") || output.contains("0000001f")
                lastState = state
                return state
            }
        } catch (_: Throwable) { }

        return lastState
    }

    /**
     * 设置门把手状态
     * @param popOut true 弹出门把手，false 收回门把手
     */
    fun setDoorHandle(context: Context, popOut: Boolean): Boolean {
        val op = if (popOut) "pop" else "retract"
        val arg = if (popOut) 1 else 0
        lastState = popOut

        // 途径一：执行 /system/bin/wm_door 独立系统控制命令（速度极快、不依赖Binder权限）
        try {
            val proc = ProcessBuilder("/system/bin/wm_door", op).redirectErrorStream(true).start()
            val output = proc.inputStream.bufferedReader().readText()
            proc.waitFor()
            Log.i(TAG, "wm_door $op result: $output")
            if (output.contains("Result: Parcel")) return true
        } catch (e: Throwable) {
            Log.w(TAG, "exec /system/bin/wm_door failed: ${e.message}")
        }

        // 途径二：使用 WmRootShell 执行
        try {
            val root = WmRootShell.find()
            if (root != null) {
                val output = root.execute("service call yfcarcontrol 72 i32 $arg", 2500)
                Log.i(TAG, "Root execute: $output")
                if (output.contains("Result: Parcel")) return true
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Root execution failed: ${e.message}")
        }

        // 途径三：使用本地 ADB Loopback 备用通道执行
        try {
            WmLocalAdb(context).use { adb ->
                adb.connect()
                val output = adb.shell("service call yfcarcontrol 72 i32 $arg")
                Log.i(TAG, "ADB loopback execute: $output")
                if (output.contains("Result: Parcel")) return true
            }
        } catch (e: Throwable) {
            Log.w(TAG, "ADB loopback failed: ${e.message}")
        }

        // 途径四：尝试原生 Binder
        val binder = getService()
        if (binder != null) {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.writeInterfaceToken(SERVICE_DESCRIPTOR)
                data.writeInt(arg)
                if (binder.transact(CODE_SET_POWER_HANDLE, data, reply, 0)) {
                    reply.readException()
                    return reply.readInt() != 0
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Direct binder rejected: ${e.message}")
            } finally {
                reply.recycle()
                data.recycle()
            }
        }

        return false
    }

    /**
     * 切换门把手状态（弹出/收回）
     */
    fun toggleDoorHandle(context: Context): Boolean {
        val current = isPoppedOut()
        val target = !current
        setDoorHandle(context, target)
        return target
    }
}
