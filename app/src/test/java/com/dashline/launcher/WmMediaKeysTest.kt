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
class WmMediaKeysTest {
    private val remote = "com.shihab.diplay.wmex5"
    private fun key(code: Int, action: Int = KeyEvent.ACTION_UP, flags: Int = 0, meta: Int = 0) =
        KeyEvent(1, 1, action, code, 0, meta, -1, 0, flags)

    @Test fun factoryReleaseOnlyPreviousAndNextBecomeArrows() {
        assertEquals(21, WmMediaKeyPolicy.arrow(key(88), remote))
        assertEquals(22, WmMediaKeyPolicy.arrow(key(87), remote))
    }
    @Test fun downAndHeldRepeatsDoNotCauseExtraFactorySkips() {
        assertNull(WmMediaKeyPolicy.arrow(key(87, KeyEvent.ACTION_DOWN), remote))
        repeat(10) {
            assertNull(WmMediaKeyPolicy.arrow(KeyEvent(1, 1, 0, 87, it + 1), remote))
        }
        assertEquals(22, WmMediaKeyPolicy.arrow(key(87), remote))
    }
    @Test fun rapidSeparateReleaseOnlyPressesAreNotDebouncedAway() {
        assertEquals(listOf(21, 21, 21, 22, 22, 22),
            listOf(88, 88, 88, 87, 87, 87).map { WmMediaKeyPolicy.arrow(key(it), remote) })
    }
    @Test fun factoryPlayersAndAbsentSourcesAreNotControlledTwice() {
        for (source in listOf(null, "", "com.ivi.musicplayer", "com.ivi.radio", "com.ivi.audiobook", "com.yftech.video")) {
            assertNull(WmMediaKeyPolicy.arrow(key(87), source))
            assertNull(WmMediaKeyPolicy.arrow(key(88), source))
        }
    }
    @Test fun canceledModifiedAndOtherKeysDoNotControlMusic() {
        assertNull(WmMediaKeyPolicy.arrow(key(87, flags = KeyEvent.FLAG_CANCELED), remote))
        assertNull(WmMediaKeyPolicy.arrow(key(87, meta = KeyEvent.META_CTRL_ON), remote))
        for (code in listOf(21, 22, 24, 25, 85, 4)) assertNull(WmMediaKeyPolicy.arrow(key(code), remote))
    }
}
