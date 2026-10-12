// SPDX-License-Identifier: GPL-3.0-only
package com.dashline.launcher

import android.os.SystemClock
import android.view.KeyEvent

/** Use one route per click; never send the same toggle to two players. */
internal class MediaKeyDispatcher(private val systemDispatch: (KeyEvent) -> Unit) {
    fun dispatch(keyCode: Int, sessionDispatch: ((KeyEvent) -> Boolean)?): Boolean {
        val now = SystemClock.uptimeMillis()
        val down = KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0)
        val up = KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0)
        if (sessionDispatch != null) {
            val accepted = try { sessionDispatch(down) } catch (_: Exception) { false }
            if (accepted) {
                // A received DOWN can already change playback. Do not toggle again.
                try { sessionDispatch(up) } catch (_: Exception) { }
                return true
            }
        }
        return try {
            systemDispatch(down)
            systemDispatch(up)
            true // Dispatched, not an acknowledgement that a player changed tracks.
        } catch (_: Exception) {
            false
        }
    }
}
