// Copyright (C) 2026 Metehan Kaygısız
// SPDX-License-Identifier: GPL-3.0-only

package com.dashline.launcher

import android.annotation.TargetApi
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.AudioManager
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.view.KeyEvent
import androidx.annotation.RequiresApi

/** Snapshot of what's currently playing, for the media widget. */
data class MediaInfo(
    val title: String,
    val artist: String,
    val art: Bitmap?,
    val isPlaying: Boolean,
    val packageName: String
)

/**
 * Watches the active MediaSession (Spotify, YouTube Music, radio apps, etc.) and
 * reports now-playing metadata + play state, plus exposes transport controls.
 *
 * Requires Android 5.0+ (MediaSessionManager) AND the user granting Notification
 * access. On failure it reports `null` (nothing to show) and the UI falls back to
 * the "launch a media app" state.
 */
@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class MediaMonitor(
    private val context: Context,
    private val onUpdate: (MediaInfo?) -> Unit
) {
    private val listenerComponent = ComponentName(context, MediaNotificationListener::class.java)
    private val sessionManager =
        context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager

    private var controller: MediaController? = null
    private var preferredToken: MediaSession.Token? = null
    private var started = false
    private var sessions: List<MediaController> = emptyList()
    private val keyDispatcher = MediaKeyDispatcher {
        (context.getSystemService(Context.AUDIO_SERVICE) as AudioManager).dispatchMediaKeyEvent(it)
    }

    private val sessionsChangedListener =
        MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            sessions = controllers.orEmpty()
            bindTo(pickActive(sessions))
        }

    private val controllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = emit()
        override fun onPlaybackStateChanged(state: PlaybackState?) = emit()
        override fun onSessionDestroyed() {
            val destroyed = controller?.sessionToken
            sessions = sessions.filter { it.sessionToken != destroyed }
            bindTo(pickActive(sessions))
        }
    }

    fun start() {
        if (started) return
        try {
            sessionManager.addOnActiveSessionsChangedListener(
                sessionsChangedListener, listenerComponent
            )
            started = true
            sessions = sessionManager.getActiveSessions(listenerComponent)
            bindTo(pickActive(sessions))
        } catch (e: SecurityException) {
            // Notification access not granted yet.
            bindTo(null)
        }
    }

    fun stop() {
        try {
            sessionManager.removeOnActiveSessionsChangedListener(sessionsChangedListener)
        } catch (e: Exception) {
            // ignore
        }
        controller?.unregisterCallback(controllerCallback)
        controller = null
        sessions = emptyList()
        started = false
    }

    fun playPause() = command(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
    fun next() = command(KeyEvent.KEYCODE_MEDIA_NEXT)
    fun previous() = command(KeyEvent.KEYCODE_MEDIA_PREVIOUS)

    private fun command(keyCode: Int): Boolean {
        val c = controller ?: return keyDispatcher.dispatch(keyCode, null)
        val state = c.playbackState
        val playing = state?.state in setOf(PlaybackState.STATE_PLAYING,
            PlaybackState.STATE_BUFFERING, PlaybackState.STATE_CONNECTING)
        val action = when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> if (playing) PlaybackState.ACTION_PAUSE else PlaybackState.ACTION_PLAY
            KeyEvent.KEYCODE_MEDIA_NEXT -> PlaybackState.ACTION_SKIP_TO_NEXT
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> PlaybackState.ACTION_SKIP_TO_PREVIOUS
            else -> 0L
        }
        if (action != 0L && (state?.actions ?: 0L) and action != 0L) {
            // CarPlay advertises transport controls without accepting Android 6 media keys.
            // Use the exact session shown on the card, including after pausing it.
            return try {
                when (action) {
                    PlaybackState.ACTION_PAUSE -> c.transportControls.pause()
                    PlaybackState.ACTION_PLAY -> c.transportControls.play()
                    PlaybackState.ACTION_SKIP_TO_NEXT -> c.transportControls.skipToNext()
                    PlaybackState.ACTION_SKIP_TO_PREVIOUS -> c.transportControls.skipToPrevious()
                }
                true
            } catch (_: Exception) { false }
        }
        return keyDispatcher.dispatch(keyCode) { event -> c.dispatchMediaButtonEvent(event) }
    }

    fun seekTo(ms: Long) {
        controller?.transportControls?.seekTo(ms)
    }

    /** Track length in ms, or 0 if unknown (e.g. live radio streams). */
    fun durationMs(): Long =
        controller?.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L

    /** Estimated current position in ms, extrapolated while playing. */
    fun positionMs(): Long {
        val s = controller?.playbackState ?: return 0L
        var pos = s.position
        if (s.state == PlaybackState.STATE_PLAYING) {
            val delta = android.os.SystemClock.elapsedRealtime() - s.lastPositionUpdateTime
            pos += (delta * s.playbackSpeed).toLong()
        }
        return pos.coerceAtLeast(0L)
    }

    // ---- internals ---------------------------------------------------------

    /** Keep the displayed source when paused, so resume cannot wake another player. */
    private fun pickActive(controllers: List<MediaController>?): MediaController? {
        if (controllers.isNullOrEmpty()) return null
        return controllers.firstOrNull {
            it.playbackState?.state == PlaybackState.STATE_PLAYING
        } ?: controllers.firstOrNull {
            it.sessionToken == (controller?.sessionToken ?: preferredToken)
        } ?: controllers.firstOrNull {
            it.playbackState?.state == PlaybackState.STATE_PAUSED && it.metadata != null
        } ?: controllers.firstOrNull { it.metadata != null } ?: controllers.first()
    }

    private fun bindTo(newController: MediaController?) {
        if (newController?.sessionToken == controller?.sessionToken) {
            emit()
            return
        }
        controller?.unregisterCallback(controllerCallback)
        controller = newController
        preferredToken = newController?.sessionToken
        controller?.registerCallback(controllerCallback)
        emit()
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private fun emit() {
        val c = controller
        if (c == null) {
            onUpdate(null)
            return
        }
        val md = c.metadata
        val state = c.playbackState
        val title = md?.getText(MediaMetadata.METADATA_KEY_TITLE)?.toString().orEmpty()
        val artist = (md?.getText(MediaMetadata.METADATA_KEY_ARTIST)
            ?: md?.getText(MediaMetadata.METADATA_KEY_ALBUM_ARTIST))?.toString().orEmpty()
        val art = md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: md?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        val isPlaying = state?.state == PlaybackState.STATE_PLAYING

        onUpdate(MediaInfo(title, artist, art, isPlaying, c.packageName))
    }
}
