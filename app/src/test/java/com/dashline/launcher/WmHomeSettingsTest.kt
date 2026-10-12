// SPDX-License-Identifier: GPL-3.0-only
package com.dashline.launcher

import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.dashline.launcher.databinding.ActivitySettingsBinding
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23])
class WmHomeSettingsTest {
    private fun check(width: Int, height: Int) {
        val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Theme_RadioLauncher)
        val page = ActivitySettingsBinding.inflate(LayoutInflater.from(context))
        SettingsTextSizing.enlarge(page.root)
        page.root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        page.root.layout(0, 0, width, height)
        val container = page.rowWmWeike.parent as ViewGroup
        assertTrue(container.getChildAt(0) === page.rowWmWeike)
        assertTrue(container.getChildAt(2) === page.rowWmDefaultHome)
        val sections = container.parent as ViewGroup
        assertSame(container, sections.getChildAt(0))
        assertTrue(sections.getChildAt(1) is TextView)
        for (row in listOf(page.rowWmWeike, page.rowWmDefaultHome)) {
            assertTrue(row.height >= 148)
            val titles = row.getChildAt(0) as ViewGroup
            for (index in 0 until titles.childCount) {
                val label = titles.getChildAt(index) as TextView
                assertTrue(label.width > 0)
                assertTrue(label.textSize >= if (index == 0) 32f else 24f)
                assertTrue(label.layout.height <= label.height - label.compoundPaddingTop - label.compoundPaddingBottom)
            }
            val toggle = row.getChildAt(1)
            assertTrue(toggle.width >= 120 && toggle.height >= 84)
            val switch = toggle as androidx.appcompat.widget.SwitchCompat
            assertTrue(switch.thumbDrawable.intrinsicWidth >= 52)
            assertTrue(switch.trackDrawable.intrinsicHeight >= 52)
            assertTrue(toggle.right <= row.width)
        }
        assertTrue(page.rowWmDefaultHome.top >= page.rowWmWeike.bottom)
    }
    @Test @Config(qualifiers = "w1080dp-h1920dp-port-mdpi")
    fun firstTwoOptionsFitPortrait() = check(1080, 1920)
    @Test @Config(qualifiers = "w800dp-h480dp-land-mdpi")
    fun firstTwoOptionsFitLandscapeAndRemainScrollable() = check(800, 480)
    @Test fun firstTwoSwitchesCannotBeTurnedOffByTouchOrOldSavedState() {
        val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Theme_RadioLauncher)
        val page = ActivitySettingsBinding.inflate(LayoutInflater.from(context))
        for (toggle in listOf(page.wmWeikeSwitch, page.wmDefaultHomeSwitch)) {
            assertTrue(toggle.isChecked)
            var repairs = 0
            toggle.setOnClickListener { repairs++ }
            toggle.performClick()
            toggle.isChecked = false
            toggle.toggle()
            assertTrue(toggle.isChecked)
            assertEquals(1, repairs)
        }
    }
}
