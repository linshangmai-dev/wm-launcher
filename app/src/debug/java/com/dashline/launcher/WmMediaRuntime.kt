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
import android.view.KeyEvent
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Disposable API 23 emulator regression: CarPlay transport controls vs a second player. */
class WmMediaRuntime : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        val output = Bundle()
        val sessions = mutableListOf<MediaSession>()
        var monitor: MediaMonitor? = null
        var home: HomeActivity? = null
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
            val page = home!!
            runOnMainSync {
                val mediaField = HomeActivity::class.java.getDeclaredField("mediaMonitor").apply { isAccessible = true }
                (mediaField.get(page) as? MediaMonitor)?.stop()
                mediaField.set(page, monitor)
                HomeActivity::class.java.getDeclaredMethod("renderMedia", MediaInfo::class.java)
                    .apply { isAccessible = true }.invoke(page, MediaInfo("CarPlay remote track", "Remote music", null, true, targetContext.packageName))
                page.window.decorView.clearFocus()
            }
            waitForIdleSync()
            check(page.hasWindowFocus())
            fun keyboard(code: Int, label: String) {
                complete = CountDownLatch(1)
                val before = calls.size
                sendKeyDownUpSync(code)
                check(complete.await(3, TimeUnit.SECONDS)) {
                    val focus = page.currentFocus
                    "Keyboard $label timed out: focus=${focus?.javaClass?.simpleName}/" +
                        (focus?.let { page.resources.getResourceEntryName(it.id) } ?: "none") +
                        ", musicShown=${page.findViewById<android.view.View>(R.id.mediaHeader).isShown}, window=${page.hasWindowFocus()}"
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
            output.putString("stream", "WM_MEDIA_OK API23: transport controls, physical left/right keys and card buttons reach current remote session only; held key skips once; other player=0")
            finish(-1, output)
        } catch (failure: Throwable) {
            output.putString("stream", "WM_MEDIA_FAILED ${failure.stackTraceToString()}")
            finish(0, output)
        } finally {
            runOnMainSync { home?.finish(); monitor?.stop(); sessions.forEach { it.release() } }
        }
    }
}
