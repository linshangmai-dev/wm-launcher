// SPDX-License-Identifier: GPL-3.0-only
package com.dashline.launcher

import android.os.Binder
import android.os.Parcel
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23])
class VehicleReadingsTest {
    @Test fun factoryUnitsMatchInstrumentPhotoAndConfirmedTotal() {
        val values = VehicleReadings(990, 397, 13, 7667, 673943)
        assertEquals("99%", values.batteryText)
        assertEquals("397 km", values.rangeText)
        assertEquals("1.3 km", values.tripAText)
        assertEquals("766.7 km", values.tripBText)
        assertEquals("67394 km", values.totalText)
        assertEquals("0%", VehicleReadings(batteryTenths = 0).batteryText)
        assertEquals("0.0 km", VehicleReadings(tripATenths = 0).tripAText)
    }

    @Test fun unavailableAndInvalidFieldsStayUnavailable() {
        for (raw in listOf(null, -1, 65535, Int.MAX_VALUE)) {
            val values = VehicleReadings(raw, raw, raw, raw, raw)
            assertEquals("--%", values.batteryText)
            assertEquals("-- km", values.rangeText)
            assertEquals("-- km", values.actualEstimatedRangeText)
            assertEquals("-- km", values.tripAText)
            assertEquals("-- km", values.tripBText)
            assertEquals("-- km", values.totalText)
        }
        assertEquals("--%", VehicleReadings(batteryTenths = 1001).batteryText)
        assertEquals("-- km", VehicleReadings(batteryTenths = 1001).actualEstimatedRangeText)
    }

    @Test fun calculatedRangeUses280KmAndTheDisplayedBatteryPercentage() {
        for ((raw, expected) in listOf(0 to "0.0 km", 500 to "140.0 km",
                980 to "274.4 km", 990 to "277.2 km", 1000 to "280.0 km")) {
            assertEquals(expected, VehicleReadings(batteryTenths = raw).actualEstimatedRangeText)
        }
        // The estimate must agree with the whole percentage shown inside the icon.
        assertEquals("98%", VehicleReadings(batteryTenths = 985).batteryText)
        assertEquals("274.4 km", VehicleReadings(batteryTenths = 985).actualEstimatedRangeText)
        // Factory range remains independent; missing SOC must never generate an estimate.
        val noSoc = VehicleReadings(rangeKm = 397)
        assertEquals("397 km", noSoc.rangeText)
        assertEquals("-- km", noSoc.actualEstimatedRangeText)
    }

    @Test fun nativeSocGetterReadsOnlyChargePercentageWithNoJavaExceptionHeader() {
        val native = object : Binder() {
            init { attachInterface(null, "yfcarcontrol") }
            override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                assertEquals(60, code)
                data.enforceInterface("yfcarcontrol")
                assertEquals(10, data.readInt())
                assertEquals(0, data.dataAvail())
                reply!!.writeInt(990)
                return true
            }
        }
        assertEquals(990, WmBatteryReader.read(native))
        assertNull(WmBatteryReader.read(null))
        assertNull(WmBatteryReader.read(object : Binder() {
            init { attachInterface(null, "other.service") }
        }))
    }

    @Test fun refusedNativeReadIsNotMisreadAsZeroPercent() {
        val native = object : Binder() {
            init { attachInterface(null, "yfcarcontrol") }
            override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean = false
        }
        assertNull(WmBatteryReader.read(native))
    }
}
