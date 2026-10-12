// SPDX-License-Identifier: GPL-3.0-only
package com.dashline.launcher

import android.os.IBinder
import android.os.Parcel

/** Raw OEM units: SOC 0.1%, remaining range km, odometers 0.1 km. */
internal data class VehicleReadings(
    val batteryTenths: Int? = null,
    val rangeKm: Int? = null,
    val tripATenths: Int? = null,
    val tripBTenths: Int? = null,
    val totalTenths: Int? = null
) {
    private val batteryPercent: Int? get() = batteryTenths?.takeIf { it in 0..1000 }?.div(10)
    val batteryText: String get() = batteryPercent?.let { "$it%" } ?: "--%"
    val rangeText: String get() = rangeKm?.takeIf { it in 0..9999 }
        ?.let { "$it km" } ?: "-- km"
    // Use the same percentage as the battery icon: 280 km × percent / 100.
    // Integer tenths keep the displayed result exact without floating-point rounding.
    val actualEstimatedRangeText: String get() = batteryPercent?.let { percent ->
        val tenths = percent * 28
        "${tenths / 10}.${tenths % 10} km"
    } ?: "-- km"
    val tripAText: String get() = tripText(tripATenths)
    val tripBText: String get() = tripText(tripBTenths)
    // The factory instrument omits the fractional kilometre in its total.
    val totalText: String get() = validOdometer(totalTenths)?.let { "${it / 10} km" } ?: "-- km"

    private fun tripText(value: Int?) = validOdometer(value)
        ?.let { "${it / 10}.${it % 10} km" } ?: "-- km"
    private fun validOdometer(value: Int?) = value?.takeIf { it in 0..9_999_999 && it != 65535 }
}

/** Native CarControl getter verified in libcarservice.so; not Java AIDL. */
internal object WmBatteryReader {
    const val SERVICE = "yfcarcontrol"
    fun read(binder: IBinder?): Int? = chargeInfo(binder, 10)?.takeIf { it in 0..1000 }

    /** Only the audited read transaction and enumerated charge fields. */
    fun chargeInfo(binder: IBinder?, type: Int): Int? {
        require(type in 0..12)
        if (binder == null || binder.interfaceDescriptor != SERVICE) return null
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(SERVICE)
            data.writeInt(type)
            if (!binder.transact(60, data, reply, 0) || reply.dataAvail() != 4) return null
            // Native reply is just one int, with no Java exception header.
            return reply.readInt().takeIf { it >= 0 && it != 65535 && it != Int.MAX_VALUE }
        } finally {
            reply.recycle()
            data.recycle()
        }
    }
}
