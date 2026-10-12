// SPDX-License-Identifier: GPL-3.0-only
package com.dashline.launcher

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23])
class WmCardConfigTest {
    private val pkg = "com.wmhome.launcher"
    private fun card(name: String, buttons: JSONArray = JSONArray()) = JSONObject()
        .put("tipName", name).put("id", "fixture-$name").put("tipType", 1)
        .put("tipTexts", JSONArray().put("原来的内容")).put("tipButtons", buttons).put("unknown", 17)
    private fun config(vararg cards: JSONObject): ByteArray {
        val strings = cards.fold(byteArrayOf()) { acc, card -> acc + WmCardConfig.encode(1, card.toString().toByteArray()) }
        val entry = WmCardConfig.encode(1, "message_card_api_model".toByteArray()) +
            WmCardConfig.encode(2, WmCardConfig.encode(6, strings) + byteArrayOf(0x48, 0x01))
        // A different Preferences entry plus future fixed-width and varint fields must survive.
        return WmCardConfig.encode(1, entry) + WmCardConfig.encode(1,
            WmCardConfig.encode(1, "other_setting".toByteArray()) + WmCardConfig.encode(2, byteArrayOf(0x08, 0x01))) +
            byteArrayOf(0x50, 0x07, 0x5d, 0x12, 0x34, 0x56, 0x78)
    }
    @Test fun allMatchingCardsAreEnabledAndRepeatedEnableDoesNotChangeBytes() {
        val input = config(card("威课堂"), card("其他卡片"), card("威课堂"))
        assertArrayEquals(input, WmCardConfig.inspect(input, pkg).bytes)
        assertFalse(WmCardConfig.inspect(input, pkg).enabled)
        val enabled = WmCardConfig.update(input, pkg, true)
        assertEquals(2, enabled.cards)
        assertTrue(WmCardConfig.inspect(enabled.bytes, pkg).enabled)
        assertArrayEquals(enabled.bytes, WmCardConfig.update(enabled.bytes, pkg, true).bytes)
        assertArrayEquals(input, WmCardConfig.update(enabled.bytes, pkg, false).bytes)
    }
    @Test fun disablePreservesOtherButtonsAndUnknownFieldsRatherThanRestoringStaleBackup() {
        val originalButton = JSONObject().put("text", "其他功能").put("type", 2).put("url", "fixture")
        val input = config(card("威课堂", JSONArray().put(originalButton)), card("其他卡片"))
        val enabled = WmCardConfig.update(input, pkg, true).bytes
        val disabled = WmCardConfig.update(enabled, pkg, false).bytes
        assertArrayEquals(input, disabled)
        assertTrue(disabled.takeLast(7).toByteArray().contentEquals(byteArrayOf(0x50, 0x07, 0x5d, 0x12, 0x34, 0x56, 0x78)))
        assertArrayEquals(input, WmCardConfig.update(input, pkg, false).bytes)
    }
    @Test fun switchingLauncherFlavorReplacesOurEntryWithoutDuplicatingIt() {
        val original = config(card("威课堂"))
        val old = WmCardConfig.update(original, "com.wm.xxqg", true).bytes
        assertFalse(WmCardConfig.inspect(old, pkg).enabled)
        val updated = WmCardConfig.update(old, pkg, true).bytes
        assertTrue(WmCardConfig.inspect(updated, pkg).enabled)
        assertArrayEquals(original, WmCardConfig.update(updated, pkg, false).bytes)
    }
    @Test fun malformedOrUnsupportedConfigsFailBeforeProducingReplacement() {
        val valid = config(card("威课堂"))
        for (bad in listOf(byteArrayOf(), valid.copyOf(4), byteArrayOf(0x0a, 0xff.toByte()),
            byteArrayOf(0x00), config(card("无匹配卡片")))) {
            assertTrue(runCatching { WmCardConfig.update(bad, pkg, true) }.isFailure)
        }
        assertTrue(runCatching { WmCardConfig.update(valid, "com.other.app", true) }.isFailure)
    }
}
