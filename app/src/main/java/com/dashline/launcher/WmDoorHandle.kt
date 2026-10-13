package com.dashline.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Parcel
import android.util.Log

/**
 * 威马 EX5 隐藏式电动门把手控制单例
 * 官方标准方案：绑定威马系统官方服务 com.yftech.carservice/.CarControlService
 * 彻底消除 ServiceManager 白名单与沙箱权限拦截问题！
 */
internal object WmDoorHandle {
    private const val TAG = "WM-DoorHandle"
    private const val SERVICE_DESCRIPTOR = "com.yftech.carmanager.ICarControl"
    private const val CODE_SET_POWER_HANDLE = 72
    private const val CODE_GET_POWER_HANDLE = 73

    @Volatile private var carBinder: IBinder? = null
    @Volatile private var bound = false
    private var lastState = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.i(TAG, "CarControlService connected! Binder descriptor: ${service?.interfaceDescriptor}")
            carBinder = service
            bound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.w(TAG, "CarControlService disconnected")
            carBinder = null
            bound = false
        }
    }

    /**
     * 在桌面启动时预绑定原厂车辆控制服务
     */
    fun bind(context: Context) {
        if (bound && carBinder != null && carBinder?.isBinderAlive == true) return
        try {
            val intent = Intent("com.yftech.CarControlService").apply {
                setClassName("com.yftech.carservice", "com.yftech.carservice.CarControlService")
            }
            context.applicationContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        } catch (e: Throwable) {
            Log.w(TAG, "bindService failed: ${e.message}")
        }
    }

    /**
     * 读取当前门把手状态
     */
    fun isPoppedOut(context: Context): Boolean {
        bind(context)
        val binder = carBinder
        if (binder != null && binder.isBinderAlive) {
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
                Log.w(TAG, "isPoppedOut failed: ${e.message}")
            } finally {
                reply.recycle()
                data.recycle()
            }
        }
        return lastState
    }

    /**
     * 设置门把手状态
     */
    fun setDoorHandle(context: Context, popOut: Boolean): Boolean {
        bind(context)
        val arg = if (popOut) 1 else 0
        lastState = popOut

        // 优先通过已绑定的 CarControlService 执行
        val binder = carBinder
        if (binder != null && binder.isBinderAlive) {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.writeInterfaceToken(SERVICE_DESCRIPTOR)
                data.writeInt(arg)
                if (binder.transact(CODE_SET_POWER_HANDLE, data, reply, 0)) {
                    reply.readException()
                    val success = reply.readInt() != 0
                    Log.i(TAG, "setDoorHandle via CarControlService success=$success")
                    return true
                }
            } catch (e: Throwable) {
                Log.w(TAG, "setDoorHandle binder error: ${e.message}")
            } finally {
                reply.recycle()
                data.recycle()
            }
        }

        // 备选方案：尝试执行 /system/bin/wm_door
        try {
            val op = if (popOut) "pop" else "retract"
            val proc = ProcessBuilder("/system/bin/wm_door", op).redirectErrorStream(true).start()
            val output = proc.inputStream.bufferedReader().readText()
            proc.waitFor()
            Log.i(TAG, "wm_door output: $output")
            if (output.contains("Result: Parcel")) return true
        } catch (_: Throwable) { }

        return false
    }

    /**
     * 切换门把手状态
     */
    fun toggleDoorHandle(context: Context): Boolean {
        val current = isPoppedOut(context)
        val target = !current
        setDoorHandle(context, target)
        return target
    }
}
