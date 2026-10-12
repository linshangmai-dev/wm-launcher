// SPDX-License-Identifier: GPL-3.0-only
package com.dashline.launcher

import android.view.KeyEvent

/** One track change per physical arrow press; preserve other keyboard navigation. */
internal class MediaArrowKeys {
    private val presses = mutableSetOf<Triple<Int, Int, Int>>()

    fun dispatch(event: KeyEvent, enabled: Boolean, skip: (Int) -> Unit): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_DPAD_LEFT && event.keyCode != KeyEvent.KEYCODE_DPAD_RIGHT) return false
        val press = Triple(event.deviceId, event.keyCode, event.scanCode)
        return when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (press in presses) true
                else if (enabled && event.repeatCount == 0 && event.hasNoModifiers()) {
                    presses.add(press)
                    skip(event.keyCode)
                    true
                } else false
            }
            KeyEvent.ACTION_UP -> presses.remove(press)
            else -> false
        }
    }

    fun reset() = presses.clear()
}
