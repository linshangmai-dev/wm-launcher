// SPDX-License-Identifier: GPL-3.0-only
package com.dashline.launcher

import java.io.ByteArrayOutputStream
import org.json.JSONArray
import org.json.JSONObject

/** The verified OEM Preferences protobuf. Unknown fields retain their exact bytes. */
internal object WmCardConfig {
    const val HOME_CLASS = "com.dashline.launcher.HomeActivity"
    private const val KEY = "message_card_api_model"
    private const val CARD = "威课堂"
    private const val BUTTON = "打开桌面"
    data class Update(val bytes: ByteArray, val cards: Int, val enabled: Boolean)
    private data class Field(val number: Int, val wire: Int, val payload: ByteArray, val raw: ByteArray)

    fun update(data: ByteArray, packageName: String, enable: Boolean): Update = transform(data, packageName, enable)
    fun inspect(data: ByteArray, packageName: String): Update = transform(data, packageName, null)

    private fun transform(data: ByteArray, packageName: String, enable: Boolean?): Update {
        require(data.size in 1..1_048_576) { "卡片配置大小异常" }
        require(packageName in setOf("com.wmhome.launcher", "com.wm.xxqg"))
        var cards = 0
        var enabledCards = 0
        var keys = 0
        val output = fields(data).map entries@{ entry ->
            if (entry.number != 1 || entry.wire != 2) return@entries entry.raw
            val parts = fields(entry.payload)
            if (parts.singleOrNull { it.number == 1 && it.wire == 2 }?.payload?.toString(Charsets.UTF_8) != KEY)
                return@entries entry.raw
            check(++keys == 1) { "卡片配置包含重复条目" }
            val value = parts.single { it.number == 2 && it.wire == 2 }
            val values = fields(value.payload)
            val set = values.single { it.number == 6 && it.wire == 2 }
            val strings = fields(set.payload).map strings@{ field ->
                if (field.number != 1 || field.wire != 2) return@strings field.raw
                val card = JSONObject(field.payload.toString(Charsets.UTF_8))
                if (card.optString("tipName") != CARD) return@strings field.raw
                cards++
                val buttons = card.optJSONArray("tipButtons") ?: JSONArray()
                fun isOurs(button: JSONObject?): Boolean = button != null &&
                    button.optInt("type") == 1 && button.optString("url") == HOME_CLASS &&
                    button.optString("packageName") in setOf("com.wmhome.launcher", "com.wm.xxqg") &&
                    button.optString("text") == BUTTON
                if (isOurs(buttons.optJSONObject(0)) &&
                    buttons.optJSONObject(0)?.optString("packageName") == packageName) enabledCards++
                if (enable == null) return@strings field.raw
                val next = JSONArray()
                if (enable) next.put(JSONObject().put("text", BUTTON).put("type", 1)
                    .put("url", HOME_CLASS).put("packageName", packageName))
                for (i in 0 until buttons.length()) if (!isOurs(buttons.optJSONObject(i))) next.put(buttons.get(i))
                // Already correct is a byte-for-byte no-op, including JSON spacing.
                if (next.toString() == buttons.toString()) return@strings field.raw
                card.put("tipButtons", next)
                encode(field.number, card.toString().toByteArray(Charsets.UTF_8))
            }.joinBytes()
            if (strings.contentEquals(set.payload)) return@entries entry.raw
            val nextValue = values.map { if (it === set) encode(6, strings) else it.raw }.joinBytes()
            val nextEntry = parts.map { if (it === value) encode(2, nextValue) else it.raw }.joinBytes()
            encode(1, nextEntry)
        }.joinBytes()
        check(keys == 1 && cards > 0) { "没有找到原厂威课堂卡片，请先打开原厂首页" }
        return Update(output, cards, if (enable == null) enabledCards == cards else enable)
    }

    private fun fields(bytes: ByteArray): List<Field> {
        var at = 0
        fun varint(): Long {
            var value = 0L
            for (shift in 0..63 step 7) {
                check(at < bytes.size) { "卡片配置不完整" }
                val b = bytes[at++].toInt() and 255
                check(shift < 63 || b <= 1) { "卡片配置整数溢出" }
                value = value or ((b and 127).toLong() shl shift)
                if (b < 128) return value
            }
            error("卡片配置整数异常")
        }
        val result = ArrayList<Field>()
        while (at < bytes.size) {
            val start = at
            val tag = varint()
            check(tag > 0 && tag <= 0xffff_ffffL) { "卡片配置字段异常" }
            val wire = (tag and 7).toInt()
            val number = (tag ushr 3).toInt()
            check(number > 0)
            val size = when (wire) {
                0 -> { val v = at; varint(); at - v }
                1 -> 8
                2 -> { val n = varint(); check(n in 0..1_048_576); n.toInt() }
                5 -> 4
                else -> error("不支持的卡片配置字段")
            }
            val payloadStart = if (wire == 0) at - size else at
            val end = if (wire == 0) at else at + size
            check(end <= bytes.size) { "卡片配置不完整" }
            at = end
            result.add(Field(number, wire, bytes.copyOfRange(payloadStart, end), bytes.copyOfRange(start, end)))
        }
        return result
    }

    internal fun encode(number: Int, payload: ByteArray): ByteArray {
        val result = ByteArrayOutputStream()
        fun put(value: Int) {
            var n = value
            while (n >= 128) { result.write((n and 127) or 128); n = n ushr 7 }
            result.write(n)
        }
        put((number shl 3) or 2)
        put(payload.size)
        result.write(payload)
        return result.toByteArray()
    }
    private fun List<ByteArray>.joinBytes(): ByteArray = ByteArrayOutputStream().also { out ->
        forEach { out.write(it) }
    }.toByteArray()
}
