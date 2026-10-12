// SPDX-License-Identifier: GPL-3.0-only
package com.dashline.launcher

import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.provider.Settings
import android.widget.FrameLayout
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23])
class AppDrawerActionsTest {
    private val app = AppInfo("测试应用", "com.wmhome.fixture", ColorDrawable(0))

    private fun withMenu(frequent: Boolean = false, check: (AppDrawerActivity, AlertDialog) -> Unit) {
        val controller = Robolectric.buildActivity(AppDrawerActivity::class.java).setup()
        val activity = controller.get()
        try {
            val grid = activity.findViewById<RecyclerView>(if (frequent) R.id.frequentGrid else R.id.appGrid)
            val adapter = grid.adapter as AppAdapter
            adapter.update(listOf(app))
            val holder = adapter.onCreateViewHolder(FrameLayout(activity), 0)
            adapter.onBindViewHolder(holder, 0)
            assertTrue(holder.itemView.performLongClick())
            val menu = ShadowDialog.getLatestDialog() as AlertDialog
            assertTrue(menu.isShowing)
            assertEquals(3, menu.listView.adapter.count)
            check(activity, menu)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    private fun select(menu: AlertDialog, index: Int) {
        menu.listView.performItemClick(null, index, index.toLong())
    }

    @Test fun detailsOpensTheSelectedPackageInAndroidSettings() = withMenu { activity, menu ->
        select(menu, 1)
        val intent = shadowOf(activity).nextStartedActivity
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, intent.action)
        assertEquals("package:${app.packageName}", intent.dataString)
        assertFalse(Prefs(activity).hiddenApps.contains(app.packageName))
    }

    @Test fun uninstallRequestsSystemConfirmationWithoutHidingTheApp() = withMenu { activity, menu ->
        select(menu, 2)
        val intent = shadowOf(activity).nextStartedActivity
        assertEquals(Intent.ACTION_UNINSTALL_PACKAGE, intent.action)
        assertEquals("package:${app.packageName}", intent.dataString)
        assertFalse(Prefs(activity).hiddenApps.contains(app.packageName))
    }

    @Test fun hidingAnAppStillWorksAndDoesNotStartSystemPages() = withMenu { activity, menu ->
        select(menu, 0)
        assertTrue(Prefs(activity).hiddenApps.contains(app.packageName))
        assertNull(shadowOf(activity).nextStartedActivity)
    }

    @Test fun cancellingFromFrequentAppsDoesNotChangeAnything() = withMenu(frequent = true) { activity, menu ->
        menu.cancel()
        assertFalse(Prefs(activity).hiddenApps.contains(app.packageName))
        assertNull(shadowOf(activity).nextStartedActivity)
    }
}
