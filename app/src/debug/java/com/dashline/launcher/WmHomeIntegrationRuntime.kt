// SPDX-License-Identifier: GPL-3.0-only
package com.dashline.launcher

import android.app.Instrumentation
import android.os.Bundle
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Emulator-only caller runs under the ordinary launcher UID, not the ADB root UID. */
class WmHomeIntegrationRuntime : Instrumentation() {
    private var action = "status"
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); action = arguments?.getString("action") ?: "status"; start() }
    override fun onStart() {
        val result = Bundle()
        val done = CountDownLatch(1)
        WmHomeIntegration.run(targetContext, action) { value ->
            result.putString("result", value.fold({ it.toString() }, { "FAILED: ${it.message}" }))
            done.countDown()
        }
        if (!done.await(30, TimeUnit.SECONDS)) result.putString("result", "FAILED: timeout")
        finish(if (result.getString("result")?.startsWith("FAILED:") == true) 0 else -1, result)
    }
}
