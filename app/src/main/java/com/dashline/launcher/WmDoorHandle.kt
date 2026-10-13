package com.dashline.launcher

import android.os.IBinder
import android.os.Parcel
import android.util.Log

/**
 * 威马 EX5 隐藏式电动门把手控制单例
 * 基于底层 yfcarcontrol (com.yftech.carmanager.ICarControl)
 * TRANSACTION_setPowerHandle = 72
 * TRANSACTION_getPowerHandle = 73
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
     * @return true 为已弹出，false 为已收回，null 为读取失败/服务不可用
     */
    fun isPoppedOut(): Boolean? {
        val binder = getService() ?: return null
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(SERVICE_DESCRIPTOR)
            val success = binder.transact(CODE_GET_POWER_HANDLE, data, reply, 0)
            if (!success) return null
            reply.readException()
            reply.readInt() != 0
        } catch (e: Throwable) {
            Log.w(TAG, "isPoppedOut failed: ${e.message}")
            null
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    /**
     * 设置门把手状态
     * @param popOut true 弹出门把手，false 收回门把手
     * @return true 表示执行成功，false 表示失败
     */
    fun setDoorHandle(popOut: Boolean): Boolean {
        val binder = getService() ?: return false
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(SERVICE_DESCRIPTOR)
            data.writeInt(if (popOut) 1 else 0)
            val success = binder.transact(CODE_SET_POWER_HANDLE, data, reply, 0)
            if (!success) return false
            reply.readException()
            reply.readInt() != 0
        } catch (e: Throwable) {
            Log.w(TAG, "setDoorHandle failed: ${e.message}")
            false
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    /**
     * 切换门把手状态（若当前弹出则收回，若当前收回则弹出；未知时默认执行弹出）
     * @return 切换后的期望状态 (true 弹出, false 收回)
     */
    fun toggleDoorHandle(): Boolean {
        val current = isPoppedOut()
        val target = if (current == true) false else true
        setDoorHandle(target)
        return target
    }
}
