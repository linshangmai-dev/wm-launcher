// SPDX-License-Identifier: GPL-3.0-only
package com.dashline.launcher

import android.app.Instrumentation
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.os.Build
import java.io.File

/** Runs against a dedicated emulator; fixture apps are never part of the delivered launcher. */
class WmRuntimeInstrumentation : Instrumentation() {
    private var args = Bundle()
    override fun onCreate(arguments: Bundle?) { args = arguments ?: Bundle(); start() }
    override fun onStart() {
        val output = mutableListOf<String>()
        try {
            fun check(name: String, value: Boolean) {
                require(value) { name }
                output += "PASS: $name"
            }
            val context = targetContext
            val prefs = Prefs(context)
            val all = AppRepository.loadLaunchableApps(context)
            check("runtime API 23", Build.VERSION.SDK_INT == 23)
            val permissions = context.packageManager.getPackageInfo(context.packageName,
                android.content.pm.PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty()
            check("no location or Internet permissions", permissions.none { it.endsWith("_LOCATION") ||
                it == "android.permission.INTERNET" })
            check("weather implementation removed", runCatching {
                Class.forName("com.dashline.launcher.WeatherRepository")
            }.isFailure)
            if (context.packageName == "com.wm.xxqg") {
                val alias = context.packageManager.getActivityInfo(ComponentName(context.packageName,
                    "com.wm.xxqg.base.activity.LauncherAcitity"), 0)
                check("factory learning entry exported and targets home", alias.enabled && alias.exported &&
                    alias.targetActivity == "com.dashline.launcher.HomeActivity")
            }
            check("does not list own HOME app", all.none { it.packageName == context.packageName })
            check("launcher aliases deduplicated", all.count { it.packageName == "com.wmhome.fixture" } == 1)
            check("third-party classification", all.any { it.packageName == "com.wmhome.fixture" && !it.isSystem && !it.isFactory })
            check("factory classification", all.any { it.packageName == "com.yftech.vehiclecenter" && it.isFactory })
            check("hiding apps", AppRepository.loadLaunchableApps(context, setOf("com.wmhome.fixture")).none { it.packageName == "com.wmhome.fixture" })
            val control = WmFactory.entries.first { it.id == "vehicle" }
            val energy = WmFactory.entries.first { it.id == "energy" }
            check("distinct same-package factory components", control.packageName == energy.packageName && control.key != energy.key)
            check("factory control is externally launchable", AppRepository.isInstalled(context, control.key))
            check("factory energy is externally launchable", AppRepository.isInstalled(context, energy.key))
            check("private Activity refused", !AppRepository.isInstalled(context, "com.wmhome.fixture/com.wmhome.fixture.PrivateActivity"))
            check("missing package fails safely", !AppRepository.launch(context, "com.wmhome.notinstalled"))
            check("original feature registry complete", WmFactory.entries.size == 19 && WmFactory.entries.map { it.id }.toSet().size == 19)
            prefs.setForRole("factory:energy", "com.wmhome.fixture")
            check("factory replacement persists", WmFactory.target(context, energy) == "com.wmhome.fixture")
            context.getSharedPreferences("radio_launcher", 0).edit().remove("wm_factory_energy")
                .putBoolean("setup_done", true).putString("wm_orientation", args.getString("orientation", "portrait"))
                .putBoolean("asked_location", true).commit()
            prefs.setFavorite(0, "com.wmhome.fixture")
            check("favorite persists across preference instances", Prefs(context).getFavorite(0) == "com.wmhome.fixture")
            startActivitySync(Intent(Intent.ACTION_MAIN).setComponent(
                ComponentName(context.packageName, "com.dashline.launcher.HomeActivity"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            waitForIdleSync()
            File(context.filesDir, "wm-runtime-tests.txt").writeText(output.joinToString("\n"))
            finish(0, Bundle().apply { putString("stream", output.joinToString("\n") + "\nWM runtime PASS tests=${output.size} API=${Build.VERSION.SDK_INT}\n") })
        } catch (error: Throwable) {
            finish(1, Bundle().apply { putString("stream", output.joinToString("\n") + "\nFAIL: " + error.stackTraceToString()) })
        }
    }
}
