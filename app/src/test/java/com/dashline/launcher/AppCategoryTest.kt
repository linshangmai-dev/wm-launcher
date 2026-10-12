// SPDX-License-Identifier: GPL-3.0-only
package com.dashline.launcher

import android.graphics.drawable.ColorDrawable
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import com.dashline.launcher.databinding.ActivityAppDrawerBinding
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23])
class AppCategoryTest {
    @Test
    fun factoryIncludesUpdatedSystemAppsAndRecognizedFactoryPackages() {
        val apps = listOf(
            AppInfo("Installed", "third", ColorDrawable(), false, false),
            AppInfo("System", "system", ColorDrawable(), true, false),
            AppInfo("Factory", "factory", ColorDrawable(), false, true),
            AppInfo("Both", "both", ColorDrawable(), true, true))
        assertEquals(listOf("third"), apps.filter { AppCategory.THIRD_PARTY.includes(it) }.map { it.packageName })
        assertEquals(listOf("system", "factory", "both"), apps.filter { AppCategory.FACTORY.includes(it) }.map { it.packageName })
        assertEquals(apps, apps.filter { AppCategory.ALL.includes(it) })
    }

    @Test @Config(qualifiers = "w1080dp-h1920dp-port-mdpi")
    fun carFilterButtonsHaveRequestedOrderAndLargeTouchTargets() {
        val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Theme_RadioLauncher)
        val drawer = ActivityAppDrawerBinding.inflate(LayoutInflater.from(context))
        drawer.root.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY))
        drawer.root.layout(0, 0, 1080, 1920)
        assertTrue(drawer.filterThirdParty.left < drawer.filterFactory.left)
        assertTrue(drawer.filterFactory.left < drawer.filterAll.left)
        for (button in listOf(drawer.filterThirdParty, drawer.filterFactory, drawer.filterAll)) {
            assertTrue(button.height >= 88)
            assertEquals(28f, button.textSize, 0.1f)
        }
        assertTrue(drawer.appGrid.height >= AppGrid.tileHeight(context, 1048) * 3)
    }
}
