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
class MediaArrowKeysTest {
    private fun key(code: Int, action: Int, repeat: Int = 0, modifiers: Int = 0) =
        KeyEvent(1, 1, action, code, repeat, modifiers)

    @Test fun leftAndRightEachSkipOnceIncludingReleaseAndHeldRepeats() {
        val mapping = MediaArrowKeys(); val received = mutableListOf<Int>()
        for (code in listOf(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT)) {
            assertTrue(mapping.dispatch(key(code, 0), true) { received.add(it) })
            repeat(8) { assertTrue(mapping.dispatch(key(code, 0, it + 1), true) { value -> received.add(value) }) }
            assertTrue(mapping.dispatch(key(code, 1), true) { received.add(it) })
        }
        assertEquals(listOf(21, 22), received)
    }
    @Test fun noMusicOrFocusedSliderPreservesNormalKeyboardNavigation() {
        val mapping = MediaArrowKeys()
        for (action in listOf(0, 1)) {
            assertFalse(mapping.dispatch(key(21, action), false) { fail("unexpected skip") })
        }
        assertFalse(mapping.dispatch(key(KeyEvent.KEYCODE_DPAD_UP, 0), true) { fail("unexpected skip") })
        assertFalse(mapping.dispatch(key(22, 0, modifiers = KeyEvent.META_CTRL_ON), true) { fail("unexpected skip") })
    }
    @Test fun releaseRemainsConsumedWhenPlaybackOrFocusChangesMidPress() {
        val mapping = MediaArrowKeys(); var skips = 0
        assertTrue(mapping.dispatch(key(22, 0), true) { skips++ })
        assertTrue(mapping.dispatch(key(22, 1), false) { skips++ })
        assertFalse(mapping.dispatch(key(22, 1), true) { skips++ })
        assertEquals(1, skips)
    }
    @Test fun nextPhysicalPressCanSkipAgainAndLeavingHomeReleasesOwnership() {
        val mapping = MediaArrowKeys(); var skips = 0
        repeat(2) {
            assertTrue(mapping.dispatch(key(21, 0), true) { skips++ })
            assertTrue(mapping.dispatch(key(21, 1), true) { skips++ })
        }
        assertEquals(2, skips)
        assertTrue(mapping.dispatch(key(22, 0), true) { skips++ })
        mapping.reset()
        assertFalse(mapping.dispatch(key(22, 0, 1), true) { skips++ })
        assertFalse(mapping.dispatch(key(22, 1), true) { skips++ })
        assertEquals(3, skips)
    }
}
