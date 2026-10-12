// SPDX-License-Identifier: GPL-3.0-only
package com.dashline.launcher

import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.content.ComponentName
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.view.KeyEvent
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Disposable API 23 emulator regression: CarPlay transport controls vs a second player. */
class WmMediaRuntime : Instrumentation() {
    private var resumedHome: HomeActivity? = null
    override fun callActivityOnResume(activity: android.app.Activity) {
        super.callActivityOnResume(activity)
        if (activity is HomeActivity) resumedHome = activity
    }
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        val output = Bundle()
        val sessions = mutableListOf<MediaSession>()
        var monitor: MediaMonitor? = null
        var home: HomeActivity? = null
        var factoryKeys: WmMediaKeysMonitor? = null
        try {
            require(Build.VERSION.SDK_INT == 23 && Build.FINGERPRINT.contains("generic"))
            require(targetContext.packageName == "com.wmhome.launcher")
            val calls = mutableListOf<String>()
            var otherCalls = 0
            var complete = CountDownLatch(1)
            lateinit var carplay: MediaSession
            lateinit var other: MediaSession
            lateinit var target: MediaController
            val main = Handler(Looper.getMainLooper())
            val actions = PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
                PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS
            fun state(value: Int) = PlaybackState.Builder().setActions(actions)
                .setState(value, 0L, 1f).build()
            runOnMainSync {
                carplay = MediaSession(targetContext, "CarPlay flags=0").also { sessions.add(it) }
                carplay.setCallback(object : MediaSession.Callback() {
                    private fun received(command: String, value: Int? = null) {
                        calls.add(command)
                        if (value != null) carplay.setPlaybackState(state(value))
                        complete.countDown()
                    }
                    override fun onPlay() = received("play", PlaybackState.STATE_PLAYING)
                    override fun onPause() = received("pause", PlaybackState.STATE_PAUSED)
                    override fun onSkipToNext() = received("next")
                    override fun onSkipToPrevious() = received("previous")
                    override fun onMediaButtonEvent(intent: android.content.Intent): Boolean {
                        calls.add("unexpected-media-key")
                        return false
                    }
                }, main)
                carplay.setMetadata(MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, "汽水音乐 / CarPlay").build())
                carplay.setPlaybackState(state(PlaybackState.STATE_PLAYING))
                carplay.isActive = true
                other = MediaSession(targetContext, "Old local player").also { sessions.add(it) }
                other.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS)
                other.setCallback(object : MediaSession.Callback() {
                    override fun onMediaButtonEvent(intent: android.content.Intent): Boolean { otherCalls++; return true }
                    override fun onPlay() { otherCalls++ }
                    override fun onPause() { otherCalls++ }
                    override fun onSkipToNext() { otherCalls++ }
                    override fun onSkipToPrevious() { otherCalls++ }
                }, main)
                other.setMetadata(MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, "Local track").build())
                other.setPlaybackState(state(PlaybackState.STATE_PAUSED))
                other.isActive = true
                target = MediaController(targetContext, carplay.sessionToken)
                check(target.flags == 0L)
                monitor = MediaMonitor(targetContext) { }
                MediaMonitor::class.java.getDeclaredField("sessions").apply { isAccessible = true }
                    .set(monitor, listOf(other.controller, target))
                MediaMonitor::class.java.getDeclaredMethod("bindTo", MediaController::class.java)
                    .apply { isAccessible = true }.invoke(monitor, target)
            }
            fun command(label: String, send: () -> Boolean) {
                complete = CountDownLatch(1)
                runOnMainSync { check(send()) { "$label was rejected" } }
                check(complete.await(3, TimeUnit.SECONDS)) { "$label callback timed out" }
                waitForIdleSync()
                check(calls.last() == label)
                check(otherCalls == 0)
            }
            command("pause") { monitor!!.playPause() }
            runOnMainSync {
                val picker = MediaMonitor::class.java.getDeclaredMethod("pickActive", List::class.java).apply { isAccessible = true }
                val chosen = picker.invoke(monitor, listOf(other.controller, target)) as MediaController
                check(chosen.sessionToken == target.sessionToken) { "Paused CarPlay was replaced" }
                monitor!!.stop()
                val resumed = picker.invoke(monitor, listOf(other.controller, target)) as MediaController
                check(resumed.sessionToken == target.sessionToken) { "Source lost on foreground return" }
                MediaMonitor::class.java.getDeclaredMethod("bindTo", MediaController::class.java)
                    .apply { isAccessible = true }.invoke(monitor, resumed)
            }
            command("play") { monitor!!.playPause() }
            command("next") { monitor!!.next() }
            command("previous") { monitor!!.previous() }
            check(calls == listOf("pause", "play", "next", "previous"))
            targetContext.getSharedPreferences("radio_launcher", 0).edit()
                .putBoolean("setup_done", true).putString("wm_orientation", "portrait")
                .putString("card_media_mode", Prefs.CARD_MEDIA).commit()
            home = startActivitySync(Intent(Intent.ACTION_MAIN).setComponent(
                ComponentName(targetContext.packageName, HomeActivity::class.java.name))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as HomeActivity
            waitForIdleSync()
            home = resumedHome ?: home
            val page = home!!
            runOnMainSync {
                val mediaField = HomeActivity::class.java.getDeclaredField("mediaMonitor").apply { isAccessible = true }
                monitor!!.stop()
                monitor = mediaField.get(page) as MediaMonitor
                // Exercise Home's live notification-authorized monitor rather than
                // replacing it while Android may still be delivering lifecycle callbacks.
                val actual = MediaMonitor::class.java.getDeclaredField("controller")
                    .apply { isAccessible = true }.get(monitor) as? MediaController
                check(actual?.sessionToken == target.sessionToken) { "Home did not bind the playing test session" }
                check(page.findViewById<android.view.View>(R.id.mediaTitle).requestFocus())
            }
            setInTouchMode(true)
            waitForIdleSync()
            check(page.hasWindowFocus())
            check(page.findViewById<android.view.View>(R.id.mediaTitle).hasFocus())
            fun keyboard(code: Int, label: String) {
                complete = CountDownLatch(1)
                val before = calls.size
                sendKeyDownUpSync(code)
                check(complete.await(3, TimeUnit.SECONDS)) {
                    val focus = page.currentFocus
                    "Keyboard $label timed out: focus=${focus?.javaClass?.simpleName}/" +
                        (focus?.let { page.resources.getResourceEntryName(it.id) } ?: "none") +
                        ", musicShown=${page.findViewById<android.view.View>(R.id.mediaHeader).isShown}, window=${page.hasWindowFocus()}, " +
                        "source=" + HomeActivity::class.java.getDeclaredField("currentMedia").apply { isAccessible = true }.get(page) +
                        ", destroyed=${page.isDestroyed}, latestHome=${resumedHome === page}"
                }
                waitForIdleSync()
                check(calls.size == before + 1 && calls.last() == label)
                check(otherCalls == 0)
            }
            keyboard(KeyEvent.KEYCODE_DPAD_LEFT, "previous")
            keyboard(KeyEvent.KEYCODE_DPAD_RIGHT, "next")
            command("previous") { page.findViewById<android.view.View>(R.id.btnPrev).performClick() }
            command("next") { page.findViewById<android.view.View>(R.id.btnNext).performClick() }
            complete = CountDownLatch(1)
            val before = calls.size
            runOnMainSync {
                check(page.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT)))
                repeat(8) {
                    check(page.dispatchKeyEvent(KeyEvent(1, 1, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT, it + 1)))
                }
                check(page.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT)))
            }
            check(complete.await(3, TimeUnit.SECONDS))
            waitForIdleSync()
            check(calls.size == before + 1 && calls.last() == "next" && otherCalls == 0)
            val input = FactoryInput()
            factoryKeys = WmMediaKeysMonitor(findService = { input }) { event ->
                HomeActivity::class.java.getDeclaredMethod("onFactoryMediaKey", KeyEvent::class.java)
                    .apply { isAccessible = true }.invoke(page, event)
            }
            runOnMainSync { factoryKeys!!.start() }
            check(input.connected.await(3, TimeUnit.SECONDS)) { "Factory subscriptions timed out" }
            check(input.keys() == setOf(87, 88)) { "Subscribed to unrelated factory keys" }
            fun factory(code: Int, label: String) {
                complete = CountDownLatch(1)
                val count = calls.size
                input.send(code, KeyEvent.ACTION_UP)
                check(complete.await(3, TimeUnit.SECONDS)) { "Factory release-only $label timed out" }
                waitForIdleSync()
                check(calls.size == count + 1 && calls.last() == label && otherCalls == 0)
            }
            repeat(3) { factory(88, "previous") }
            repeat(3) { factory(87, "next") }
            val count = calls.size
            input.send(87, KeyEvent.ACTION_DOWN)
            waitForIdleSync()
            check(calls.size == count)
            runOnMainSync {
                HomeActivity::class.java.getDeclaredMethod("renderMedia", MediaInfo::class.java)
                    .apply { isAccessible = true }.invoke(page, MediaInfo("Factory player", "", null, true, "com.ivi.musicplayer"))
            }
            input.send(87, KeyEvent.ACTION_UP)
            waitForIdleSync()
            check(calls.size == count) { "Factory player received a duplicate skip" }
            runOnMainSync {
                HomeActivity::class.java.getDeclaredMethod("renderMedia", MediaInfo::class.java)
                    .apply { isAccessible = true }.invoke(page, MediaInfo("Remote track", "", null, true, targetContext.packageName))
            }
            val oldCallback = input.callback(87)
            runOnMainSync { factoryKeys!!.stop() }
            check(input.disconnected.await(3, TimeUnit.SECONDS)) { "Factory listeners not removed" }
            check(input.keys().isEmpty())
            input.deliver(oldCallback, 87, KeyEvent.ACTION_UP)
            waitForIdleSync()
            check(calls.size == count) { "Stopped factory listener forwarded a late callback" }
            output.putString("stream", "WM_MEDIA_OK API23: transport controls, physical left/right keys and card buttons reach current remote session only; first arrow after touch mode works; held key skips once; factory Binder release-only 88/87 reaches same source, rapid presses retained, subscriptions removed on stop; factory player not doubled; other player=0")
            finish(-1, output)
        } catch (failure: Throwable) {
            output.putString("stream", "WM_MEDIA_FAILED ${failure.stackTraceToString()}")
            finish(0, output)
        } finally {
            runOnMainSync { factoryKeys?.stop(); home?.finish(); monitor?.stop(); sessions.forEach { it.release() } }
        }
    }

    /** The independently checked OEM contract; it dispatches releases, not keyboard DOWNs. */
    private class FactoryInput : Binder() {
        private val listeners = mutableMapOf<Int, IBinder>()
        val connected = CountDownLatch(2)
        val disconnected = CountDownLatch(2)
        init { attachInterface(null, "com.yftech.inputmanager.IInputManager") }
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code == INTERFACE_TRANSACTION) { reply?.writeString("com.yftech.inputmanager.IInputManager"); return true }
            check(code == 1 || code == 2) { "Unexpected factory operation $code" }
            data.enforceInterface("com.yftech.inputmanager.IInputManager")
            val callback = data.readStrongBinder()
            val key = data.readInt()
            check(key == 87 || key == 88)
            synchronized(listeners) {
                if (code == 1) { listeners[key] = callback; connected.countDown() }
                else { check(listeners.remove(key) === callback); disconnected.countDown() }
            }
            reply!!.writeNoException(); reply.writeInt(1)
            return true
        }
        fun keys(): Set<Int> = synchronized(listeners) { listeners.keys.toSet() }
        fun callback(key: Int): IBinder = synchronized(listeners) { listeners.getValue(key) }
        fun send(key: Int, action: Int) = deliver(callback(key), key, action)
        fun deliver(callback: IBinder, key: Int, action: Int) {
            val data = Parcel.obtain()
            try {
                data.writeInterfaceToken("com.yftech.inputmanager.IInputListener")
                data.writeInt(1)
                KeyEvent(action, key).writeToParcel(data, 0)
                check(callback.transact(1, data, null, IBinder.FLAG_ONEWAY))
            } finally { data.recycle() }
        }
    }
}
