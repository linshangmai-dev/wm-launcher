// SPDX-License-Identifier: GPL-3.0-only
package com.dashline.launcher

import android.view.KeyEvent
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23])
class MediaKeyDispatcherTest {
    @Test fun withoutNotificationAccessAllThreeCommandsReachSystemWithPairedEvents() {
        val received = mutableListOf<KeyEvent>()
        val dispatcher = MediaKeyDispatcher { received.add(it) }
        for (key in listOf(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_MEDIA_PREVIOUS)) {
            assertTrue(dispatcher.dispatch(key, null))
        }
        assertEquals(listOf(85, 85, 87, 87, 88, 88), received.map { it.keyCode })
        assertEquals(listOf(0, 1, 0, 1, 0, 1), received.map { it.action })
        received.chunked(2).forEach { assertEquals(it[0].downTime, it[1].downTime) }
    }

    @Test fun acceptedSessionToggleIsNeverRepeatedThroughGlobalPlayer() {
        val global = mutableListOf<KeyEvent>()
        val session = mutableListOf<KeyEvent>()
        var playing = true
        val dispatcher = MediaKeyDispatcher { global.add(it) }
        val receiver: (KeyEvent) -> Boolean = {
            session.add(it)
            if (it.action == KeyEvent.ACTION_DOWN) playing = !playing
            true
        }
        assertTrue(dispatcher.dispatch(85, receiver))
        assertFalse(playing)
        assertTrue(global.isEmpty())
        assertTrue(dispatcher.dispatch(85, receiver))
        assertTrue(playing)
        assertEquals(4, session.size)
    }

    @Test fun rejectedOrUnavailableSessionFallsBackAndFailedSystemReportsFailure() {
        val received = mutableListOf<KeyEvent>()
        val dispatcher = MediaKeyDispatcher { received.add(it) }
        assertTrue(dispatcher.dispatch(87) { false })
        assertTrue(dispatcher.dispatch(88) { throw SecurityException("session unavailable") })
        assertEquals(listOf(87, 87, 88, 88), received.map { it.keyCode })
        assertFalse(MediaKeyDispatcher { throw SecurityException("service unavailable") }.dispatch(85, null))
    }

    @Test fun failureAfterAcceptedDownCannotTriggerASecondToggle() {
        var calls = 0
        val global = mutableListOf<KeyEvent>()
        assertTrue(MediaKeyDispatcher { global.add(it) }.dispatch(85) {
            calls++
            if (it.action == KeyEvent.ACTION_UP) throw IllegalStateException("session destroyed")
            true
        })
        assertEquals(2, calls)
        assertTrue(global.isEmpty())
    }
}
