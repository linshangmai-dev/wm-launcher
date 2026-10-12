// SPDX-License-Identifier: GPL-3.0-only
package com.dashline.launcher

import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.Parcel
import android.util.Log
import android.view.KeyEvent

/** Factory input sends release-only media keys, outside the focused window's keyboard path. */
internal object WmMediaKeyPolicy {
    fun arrow(event: KeyEvent, source: String?): Int? {
        // These players already subscribe to this OEM service themselves.
        // Forwarding their release a second time could skip two tracks.
        if (source.isNullOrBlank() || source.startsWith("com.ivi.") || source.startsWith("com.yftech.")) return null
        if (event.action != KeyEvent.ACTION_UP || event.isCanceled || !event.hasNoModifiers()) return null
        return when (event.keyCode) {
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> KeyEvent.KEYCODE_DPAD_LEFT
            KeyEvent.KEYCODE_MEDIA_NEXT -> KeyEvent.KEYCODE_DPAD_RIGHT
            else -> null
        }
    }
}

/**
 * Listen to the verified Android 6 WM input Binder only while Home is resumed.
 * Subscribes to 87/88 without overriding any factory key receiver or injecting
 * global keys. No vendor SDK binary, new permission, root or background service.
 * Contract: Service.Input / IInputManager request=1, remove=2; callback=1.
 */
internal class WmMediaKeysMonitor(
    private val findService: () -> IBinder? = {
        Class.forName("android.os.ServiceManager").getMethod("getService", String::class.java)
            .invoke(null, SERVICE) as? IBinder
    },
    private val onKey: (KeyEvent) -> Unit
) {
    private val main = Handler(Looper.getMainLooper())
    private var session: Session? = null

    fun start() {
        if (Build.VERSION.SDK_INT != 23 || session != null) return
        val next = Session()
        session = next
        next.worker.post { next.connect() }
    }

    fun stop() {
        val old = session ?: return
        session = null
        old.active = false
        old.worker.removeCallbacksAndMessages(null)
        old.worker.post { old.detach(); old.thread.quitSafely() }
    }

    private inner class Session {
        val thread = HandlerThread("WM-MediaKeys").apply { start() }
        val worker = Handler(thread.looper)
        @Volatile var active = true
        private var remote: IBinder? = null
        private val subscriptions = mutableSetOf<Int>()
        private val death = IBinder.DeathRecipient {
            worker.post {
                detach()
                if (active) worker.postDelayed({ connect() }, 1000)
            }
        }
        private val listener = object : Binder() {
            init { attachInterface(null, CALLBACK) }
            override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                if (code == INTERFACE_TRANSACTION) {
                    reply?.writeString(CALLBACK)
                    return true
                }
                if (code != 1) return super.onTransact(code, data, reply, flags)
                data.enforceInterface(CALLBACK)
                val event = if (data.readInt() != 0) KeyEvent.CREATOR.createFromParcel(data) else null
                if (event != null && event.keyCode in KEYS) main.post {
                    if (active && session === this@Session) onKey(event)
                }
                reply?.writeNoException()
                return true
            }
        }

        fun connect() {
            if (!active) return
            detach()
            try {
                val binder = findService() ?: return
                check(binder.interfaceDescriptor == DESCRIPTOR) { "Unexpected input descriptor" }
                remote = binder
                binder.linkToDeath(death, 0)
                for (code in KEYS) {
                    check(subscription(1, code)) { "Factory input subscription rejected" }
                    subscriptions.add(code)
                }
                Log.i(TAG, "Factory previous/next listener connected")
            } catch (error: Exception) {
                detach()
                Log.w(TAG, "Factory input unavailable: ${error.javaClass.simpleName}")
            }
        }

        private fun subscription(transaction: Int, key: Int): Boolean {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            return try {
                data.writeInterfaceToken(DESCRIPTOR)
                data.writeStrongBinder(listener)
                data.writeInt(key)
                check(remote?.transact(transaction, data, reply, 0) == true)
                reply.readException()
                reply.readInt() != 0
            } finally { data.recycle(); reply.recycle() }
        }

        fun detach() {
            for (code in subscriptions) runCatching { subscription(2, code) }
            subscriptions.clear()
            runCatching { remote?.unlinkToDeath(death, 0) }
            remote = null
        }
    }

    companion object {
        const val SERVICE = "Service.Input"
        const val DESCRIPTOR = "com.yftech.inputmanager.IInputManager"
        const val CALLBACK = "com.yftech.inputmanager.IInputListener"
        private const val TAG = "WM-MediaKeys"
        private val KEYS = listOf(KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_MEDIA_NEXT)
    }
}
