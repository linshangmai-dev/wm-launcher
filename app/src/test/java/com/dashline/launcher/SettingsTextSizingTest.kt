// SPDX-License-Identifier: GPL-3.0-only
package com.dashline.launcher

import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.dashline.launcher.databinding.ActivitySettingsBinding
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23])
class SettingsTextSizingTest {
    private fun labels(root: View): List<TextView> = buildList {
        if (root is TextView) add(root)
        if (root is ViewGroup) for (index in 0 until root.childCount) addAll(labels(root.getChildAt(index)))
    }

    private fun checkPage(width: Int, height: Int) {
        val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Theme_RadioLauncher)
        val page = ActivitySettingsBinding.inflate(LayoutInflater.from(context))
        page.header.pageTitle.setText(R.string.settings_title)
        page.languageValue.text = "简体中文"
        page.versionValue.text = BuildConfig.VERSION_NAME
        val original = labels(page.root).associateWith { it.textSize }
        val delta = 4f * context.resources.displayMetrics.scaledDensity
        SettingsTextSizing.enlarge(page.root)
        SettingsTextSizing.enlarge(page.root)
        page.root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        page.root.layout(0, 0, width, height)
        assertTrue(original.size > 30)
        for ((label, size) in original) {
            assertEquals(size + delta, label.textSize, 0.1f)
            if (label.text.isNotEmpty()) {
                assertTrue("No width for ${label.text}", label.width > 0)
                assertNotNull(label.layout)
                assertTrue("Text clipped: ${label.text}",
                    label.layout.height <= label.height - label.compoundPaddingTop - label.compoundPaddingBottom)
            }
        }
    }

    @Test @Config(qualifiers = "w1080dp-h1920dp-port-mdpi")
    fun portraitSettingsEnlargeEveryLabelOnceAndKeepRowsTallEnough() = checkPage(1080, 1920)

    @Test @Config(qualifiers = "w800dp-h480dp-land-mdpi")
    fun landscapeSettingsKeepEnlargedLabelsInsideScrollableRows() = checkPage(800, 480)

    @Test
    fun dialogTitleMessageButtonsAndLaterListRowsAreEnlargedOnlyOnce() {
        val controller = Robolectric.buildActivity(SettingsActivity::class.java).setup()
        val activity = controller.get()
        val control = AlertDialog.Builder(activity).setTitle("桌面设置")
            .setMessage("许可证和版本说明").setPositiveButton(android.R.string.ok, null).show()
        val naturalSizes = labels(control.window!!.decorView).associate { it.id to it.textSize }
        control.dismiss()
        val dialog = SettingsTextSizing.show(AlertDialog.Builder(activity)
            .setTitle("桌面设置").setMessage("许可证和版本说明")
            .setPositiveButton(android.R.string.ok, null))
        val original = labels(dialog.window!!.decorView).associateWith { it.textSize }
        assertTrue(original.values.all { it > 0f })
        for ((label, size) in original) {
            assertEquals(naturalSizes.getValue(label.id) + 4f * activity.resources.displayMetrics.scaledDensity,
                size, 0.1f)
        }
        assertNotNull(dialog.getButton(AlertDialog.BUTTON_POSITIVE).getTag(R.id.wm_settings_text_enlarged))
        SettingsTextSizing.enlarge(dialog.window!!.decorView)
        for ((label, size) in original) assertEquals(size, label.textSize, 0.1f)
        dialog.dismiss()

        val listDialog = SettingsTextSizing.show(AlertDialog.Builder(activity)
            .setTitle("显示方向").setItems(arrayOf("跟随屏幕", "竖屏", "横屏"), null))
        val list = listDialog.listView!!
        list.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY))
        list.layout(0, 0, 400, 400)
        assertTrue(list.childCount > 0)
        for (label in labels(list)) {
            assertNotNull("Lazy row was not enlarged: ${label.text}", label.getTag(R.id.wm_settings_text_enlarged))
            assertTrue(label.textSize >= 20f * activity.resources.displayMetrics.scaledDensity)
        }
        listDialog.dismiss()
        controller.pause().stop().destroy()
    }
}
