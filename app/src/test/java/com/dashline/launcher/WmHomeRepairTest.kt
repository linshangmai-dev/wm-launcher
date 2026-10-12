// SPDX-License-Identifier: GPL-3.0-only
package com.dashline.launcher

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23])
class WmHomeRepairTest {
    @Test fun alreadyEnabledDoesNotWriteEitherSetting() {
        val result = WmHomeRepair.ensure({ true }, { error("unexpected home write") },
            { true }, { error("unexpected card write") })
        assertTrue(result.getBoolean("complete"))
    }
    @Test fun missingSettingsAreEnabledOnceAndSubsequentCheckOnlyReads() {
        var home = false; var card = false; var homeWrites = 0; var cardWrites = 0
        repeat(2) {
            val result = WmHomeRepair.ensure({ home }, { home = true; homeWrites++ },
                { card }, { card = true; cardWrites++ })
            assertTrue(result.getBoolean("complete"))
        }
        assertEquals(1, homeWrites); assertEquals(1, cardWrites)
    }
    @Test fun missingEntryDoesNotRewriteDefaultHome() {
        var card = false
        val result = WmHomeRepair.ensure({ true }, { error("unexpected home write") },
            { card }, { card = true })
        assertTrue(result.getBoolean("homeEnabled")); assertTrue(result.getBoolean("cardEnabled"))
    }
    @Test fun unreadableCardStillRepairsHomeAndReportsPartialResult() {
        var home = false
        val result = WmHomeRepair.ensure({ home }, { home = true },
            { throw IllegalStateException("原厂卡片未初始化") }, { error("unexpected write") })
        assertTrue(result.getBoolean("homeEnabled"))
        assertFalse(result.getBoolean("complete")); assertFalse(result.has("cardEnabled"))
        assertTrue(result.getString("message").contains("原厂卡片未初始化"))
    }
    @Test fun failedHomeStillRepairsCardAndUnpersistedSettingRemainsIncomplete() {
        var card = false
        val result = WmHomeRepair.ensure({ false }, { }, { card }, { card = true })
        assertFalse(result.getBoolean("homeEnabled")); assertTrue(result.getBoolean("cardEnabled"))
        assertFalse(result.getBoolean("complete"))
        assertTrue(result.getString("message").contains("系统没有保存修改"))
    }
}
