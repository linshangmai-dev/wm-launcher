// SPDX-License-Identifier: GPL-3.0-only
package com.dashline.launcher

import android.os.Binder
import android.os.Parcel
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.RuntimeEnvironment
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23])
class WmDetailsTest {
    @Test fun providerDataUsesFactoryColumnNamesAndClosesCursors() {
        var closed = 0
        val provider = object : ContentProvider() {
            override fun onCreate() = true
            override fun getType(uri: Uri) = "vnd.android.cursor.item/status"
            override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
                selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
                assertNull(selection)
                val values: Map<String, Any> = when (uri.lastPathSegment) {
                    "lte_info" -> mapOf("signal" to 99, "regstatus" to 0, "access" to 0)
                    "version_info" -> mapOf("ble" to "B", "ecu" to "E", "hw" to "H", "mcu" to "M", "rte" to "RTE-TEST", "mpu" to "P", "supplier" to "S")
                    "gps_info" -> mapOf("status" to 0, "inview" to 3, "inuse" to 0, "longitude" to 0, "latitude" to 0, "altitude" to 0)
                    "ble_info" -> mapOf("status" to 0)
                    "tbox_status" -> mapOf("connstatus" to 1)
                    else -> error("Only selected status tables may be queried")
                }
                val columns = requireNotNull(projection).map { it }.toTypedArray()
                return object : MatrixCursor(columns) {
                    override fun close() { closed++; super.close() }
                }.apply { addRow(columns.map { check(it in values); values[it] }) }
            }
            override fun insert(uri: Uri, values: ContentValues?): Uri? = error("No provider writes")
            override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = error("No provider writes")
            override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = error("No provider writes")
        }
        ShadowContentResolver.registerProviderInternal("com.yftech.tboxprovider.TboxProvider", provider)
        val reader = WmDetailsReader(RuntimeEnvironment.getApplication())
        assertEquals("未注册", reader.lte().registrationText)
        val rows = reader.connections()
        assertEquals("RTE-TEST", rows.first { it.label == "通信模块版本" }.value)
        assertEquals("已连接", rows.first { it.label == "TBOX 连接" }.value)
        assertEquals("未定位", rows.first { it.label == "经度" }.value)
        assertEquals(6, closed)
    }

    @Test fun originalRegistrationEnumsDoNotTurnUnknownStrengthIntoFullSignal() {
        val unavailable = WmLteStatus()
        assertFalse(unavailable.connected)
        assertEquals("不可用", unavailable.registrationText)
        val modem = WmLteStatus(99, 0, 0)
        assertFalse(modem.connected)
        assertEquals("未知", modem.signalText)
        assertEquals("未注册", modem.registrationText)
        assertTrue(WmLteStatus(16, 1, 6).connected)
        assertEquals("LTE", WmLteStatus(16, 1, 6).accessText)
        assertEquals("漫游", WmLteStatus(16, 5, 6).registrationText)
        assertFalse(WmLteStatus(16, 3, 6).connected)
    }

    @Test fun unverifiedUnitsAndUnpluggedCurrentAreNotFabricatedMeasurements() {
        assertEquals("100.0%", WmDetailFormat.health(1000))
        assertEquals("98%", WmDetailFormat.soc(980))
        assertEquals("不可用", WmDetailFormat.soc(null))
        assertEquals("不可用", WmDetailFormat.health(65535))
        assertEquals("65（单位待核验）", WmDetailFormat.pending(65))
        assertEquals("不可用", WmDetailFormat.pending(255))
        assertEquals("未充电", WmDetailFormat.chargeOnly(0, 64))
        assertEquals("不可用", WmDetailFormat.chargeOnly(null, 64))
        assertEquals("不可用", WmDetailFormat.flag(255, "开启", "关闭"))
    }

    @Test fun detailedChargeReadsUseOnlyNativeGetterAndNoJavaHeader() {
        val native = object : Binder() {
            init { attachInterface(null, WmBatteryReader.SERVICE) }
            override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                assertEquals(60, code)
                data.enforceInterface(WmBatteryReader.SERVICE)
                assertEquals(11, data.readInt())
                assertEquals(0, data.dataAvail())
                reply!!.writeInt(90)
                return true
            }
        }
        assertEquals(90, WmBatteryReader.chargeInfo(native, 11))
        try { WmBatteryReader.chargeInfo(native, 13); fail("unreviewed type must be rejected") }
        catch (_: IllegalArgumentException) { }
    }

    @Test fun carInfoReadsRequireJavaHeaderCorrectDescriptorAndAuditedTypes() {
        val info = object : Binder() {
            init { attachInterface(null, WmDetailBinder.INFO_DESCRIPTOR) }
            override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                assertEquals(17, code)
                data.enforceInterface(WmDetailBinder.INFO_DESCRIPTOR)
                assertEquals(4, data.readInt())
                assertEquals(0, data.dataAvail())
                reply!!.writeNoException()
                reply.writeInt(1000)
                return true
            }
        }
        assertEquals(1000, WmDetailBinder.info(info, 17, 4))
        assertNull(WmDetailBinder.info(null, 17, 4))
        assertNull(WmDetailBinder.info(Binder(), 17, 4))
        try { WmDetailBinder.info(info, 41, 0); fail("writes must be rejected") }
        catch (_: IllegalArgumentException) { }
        val denied = object : Binder() {
            init { attachInterface(null, WmDetailBinder.INFO_DESCRIPTOR) }
            override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                reply!!.writeException(SecurityException("denied"))
                return true
            }
        }
        assertNull(WmDetailBinder.info(denied, 11))
    }
}
