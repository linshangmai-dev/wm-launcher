// Copyright (C) 2026 Metehan Kaygısız
// SPDX-License-Identifier: GPL-3.0-only

package com.dashline.launcher

import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings

/** Helpers for checking / requesting to be the device's default HOME app. */
object DefaultLauncher {

    fun isDefault(context: Context): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val res = context.packageManager.resolveActivity(intent, 0)
        return res?.activityInfo?.packageName == context.packageName
    }

    /**
     * Ask the system to make us the default launcher. On Android 10+ this shows the
     * clean role dialog; on older versions it opens the Home-app settings screen.
     */
    fun request(activity: Activity, requestCode: Int): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val rm = activity.getSystemService(RoleManager::class.java)
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME) &&
                !rm.isRoleHeld(RoleManager.ROLE_HOME)
            ) {
                activity.startActivityForResult(
                    rm.createRequestRoleIntent(RoleManager.ROLE_HOME), requestCode
                )
                return true
            }
        }
        return openHomeSettings(activity)
    }

    private fun openHomeSettings(context: Context): Boolean {
        return try {
            context.startActivity(
                Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            true
        } catch (e: Exception) {
            android.widget.Toast.makeText(context, R.string.wm_home_fallback,
                android.widget.Toast.LENGTH_LONG).show()
            false
        }
    }
}
