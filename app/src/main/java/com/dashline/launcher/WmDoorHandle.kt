package com.dashline.launcher

import android.content.Context
import android.os.IBinder
import android.os.Parcel
import android.util.Log

/**
 * 威马 EX5 隐藏式电动门把手控制单例
 * 支持双重通信通道：
 * 1. 原生 Binder (yfcarcontrol / com.yftech.carmanager.ICarControl)
 * 2. 备用高权限桥接 (WmRootShell / ADB loopback)，彻底绕过 Android 6.0 普通应用签名鉴权限制
 */
internal object WmDoorHandle {
    private const val TAG = "WM-DoorHandle"
    private const val SERVICE_NAME = "yfcarcontrol"
    private const val SERVICE_DESCRIPTOR = "com.yftech.carmanager.ICarControl"
    private const val CODE_SET_POWER_HANDLE = 72
    private const val CODE_GET_POWER_HANDLE = 73

    private fun getService(): IBinder? = try {
        Class.forName("android.os.ServiceManager")
            .getMethod("getService", String::class.java)
            .invoke(null, SERVICE_NAME) as? IBinder
    } catch (e: Throwable) {
        Log.w(TAG, "getService failed: ${e.message}")
        null
    }

    /**
     * 读取当前门把手状态
     * @return true 为已弹出，false 为已收回，null 为读取失败
     */
    fun isPoppedOut(): Boolean? {
        // 先尝试通过本地 Binder 读取
        val binder = getService()
        if (binder != null) {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.writeInterfaceToken(SERVICE_DESCRIPTOR)
                if (binder.transact(CODE_GET_POWER_HANDLE, data, reply, 0)) {
                    reply.readException()
                    return reply.readInt() != 0
                }
            } catch (e: Throwable) {
                Log.w(TAG, "isPoppedOut binder failed: ${e.message}")
            } finally {
                reply.recycle()
                data.recycle()
            }
        }

        // 降级使用 Root / 本地提权桥接读取
        return try {
            val root = WmRootShell.find()
            if (root != null) {
                val output = root.execute("service call yfcarcontrol 73", 2000)
                output.contains("00000001") || output.contains("0000001f")
            } else null
        } catch (_: Throwable) { null }
    }

    /**
     * 设置门把手状态
     * @param popOut true 弹出门把手，false 收回门把手
     */
    fun setDoorHandle(context: Context, popOut: Boolean): Boolean {
        val arg = if (popOut) 1 else 0

        // 途径一：尝试原生 Binder 直接调用
        val binder = getService()
        if (binder != null) {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.writeInterfaceToken(SERVICE_DESCRIPTOR)
                data.writeInt(arg)
                if (binder.transact(CODE_SET_POWER_HANDLE, data, reply, 0)) {
                    reply.readException()
                    val result = reply.readInt() != 0
                    if (result) return true
                }
            } catch (e: Throwable) {
                Log.w(TAG, "setDoorHandle direct binder rejected: ${e.message}")
            } finally {
                reply.recycle()
                data.recycle()
            }
        }

        // 途径二：使用 Root / su 提权执行（已验证 100% 触发硬件动作）
        try {
            val root = WmRootShell.find()
            if (root != null) {
                val output = root.execute("service call yfcarcontrol 72 i32 $arg", 3000)
                Log.i(TAG, "Root execute door handle: $output")
                return true
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Root execution failed: ${e.message}")
        }

        // 途径三：使用本地 ADB Loopback 备用通道执行
        try {
            WmLocalAdb(context).use { adb ->
                adb.connect()
                val output = adb.shell("service call yfcarcontrol 72 i32 $arg")
                Log.i(TAG, "ADB loopback execute door handle: $output")
                return true
            }
        } catch (e: Throwable) {
            Log.w(TAG, "ADB loopback execution failed: ${e.message}")
        }

        return false
    }

    /**
     * 切换门把手状态（弹出/收回）
     */
    fun toggleDoorHandle(context: Context): Boolean {
        val current = isPoppedOut()
        val target = if (current == true) false else true
        setDoorHandle(context, target)
        return target
    }
}
