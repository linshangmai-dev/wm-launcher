// SPDX-License-Identifier: GPL-3.0-only
package com.dashline.launcher

import android.view.ContextThemeWrapper
import android.content.ComponentName
import android.content.res.Configuration
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import com.dashline.launcher.databinding.ItemAppBinding
import com.dashline.launcher.databinding.ItemFactoryAppBinding
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23])
class LargeAppLayoutTest {
    private fun context() = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Theme_RadioLauncher)

    private fun measure(view: View, width: Int, maximumHeight: Int = 0) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(maximumHeight,
                if (maximumHeight > 0) View.MeasureSpec.AT_MOST else View.MeasureSpec.UNSPECIFIED))
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
    }

    @Test
    @Config(qualifiers = "w1080dp-h1920dp-port-mdpi")
    fun wideCarPortraitHasLargeIconsAndCompleteTwoLineLabels() {
        val tile = ItemAppBinding.inflate(LayoutInflater.from(context()))
        tile.appLabel.text = "车载第三方应用完整名称"
        measure(tile.root, 336)
        assertEquals(128, tile.appIcon.width)
        assertEquals(29f, tile.appLabel.textSize, 0.1f)
        assertEquals(2, tile.appLabel.maxLines)
        assertTrue(tile.appLabel.bottom <= tile.root.height - tile.root.paddingBottom)
        assertTrue(AppGrid.tileHeight(context(), 1008) >= tile.root.height)
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-port-mdpi")
    fun narrowPortraitKeepsIconAndLabelInsideOneThirdOfScreen() {
        val tile = ItemAppBinding.inflate(LayoutInflater.from(context()))
        tile.appLabel.text = "行车记录仪"
        measure(tile.root, 104)
        assertTrue(tile.appIcon.width >= 80)
        assertTrue(tile.appIcon.right <= tile.root.width - tile.root.paddingRight)
        assertTrue(tile.appIcon.left >= tile.root.paddingLeft)
        assertTrue(tile.appLabel.bottom <= tile.root.height - tile.root.paddingBottom)
    }

    @Test
    @Config(qualifiers = "w1080dp-h1920dp-port-mdpi")
    fun factoryTileKeepsLargeLabelAndAvailabilityStatusVisible() {
        val tile = ItemFactoryAppBinding.inflate(LayoutInflater.from(context()))
        tile.factoryLabel.text = "车辆控制与能量管理"
        tile.factoryStatus.text = "原厂应用可用"
        measure(tile.root, 336)
        assertEquals(128, tile.factoryIcon.width)
        assertEquals(29f, tile.factoryLabel.textSize, 0.1f)
        assertTrue(tile.factoryLabel.bottom <= tile.factoryStatus.top)
        assertTrue(tile.factoryStatus.bottom <= tile.root.height - tile.root.paddingBottom)
    }

    @Test
    fun entryVariantExportsTheExactFactoryLearningComponent() {
        val ctx = context()
        val name = if (ctx.packageName == "com.wm.xxqg")
            "com.wm.xxqg.base.activity.LauncherAcitity" else "com.dashline.launcher.HomeActivity"
        val info = ctx.packageManager.getActivityInfo(ComponentName(ctx.packageName, name), 0)
        assertTrue(info.exported)
        if (ctx.packageName == "com.wm.xxqg") assertEquals("com.dashline.launcher.HomeActivity", info.targetActivity)
    }

    @Test
    @Config(qualifiers = "w1080dp-h1920dp-port-mdpi")
    fun largerSystemFontKeepsCompleteLabelsAndEnoughPageRowHeight() {
        val normalHeight = AppGrid.tileHeight(context(), 1008)
        val app = RuntimeEnvironment.getApplication()
        val configuration = Configuration(app.resources.configuration).apply { fontScale = 1.6f }
        val largeTextContext = ContextThemeWrapper(app.createConfigurationContext(configuration), R.style.Theme_RadioLauncher)
        val tile = ItemAppBinding.inflate(LayoutInflater.from(largeTextContext))
        tile.appLabel.text = "车辆控制与能量管理"
        measure(tile.root, 336)
        val scaledHeight = AppGrid.tileHeight(largeTextContext, 1008)
        assertTrue("Text must use the larger font scale: ${tile.appLabel.textSize}", tile.appLabel.textSize > 29f)
        assertTrue("Normal $normalHeight, scaled $scaledHeight, text ${tile.appLabel.textSize}", scaledHeight >= normalHeight)
        assertTrue(scaledHeight >= tile.root.height)
        assertTrue(tile.appLabel.bottom <= tile.root.height - tile.root.paddingBottom)
    }

    @Test @Config(qualifiers = "w1080dp-h1920dp-port-mdpi")
    fun threeHomeRowsFitPlayingDashboardWithoutShrinkingIconsOrTwoLineLabels() {
        val tile = ItemAppBinding.inflate(LayoutInflater.from(context()))
        tile.appLabel.text = "车载第三方应用完整名称"
        measure(tile.root, 336)
        // The photographed playing dashboard leaves about 650px after the
        // music progress row, shortcuts, header, pager and bottom navigation.
        val height = AppGrid.homeTileHeight(context(), 1008, 650)
        assertTrue(height * 3 <= 650)
        assertTrue(height >= tile.root.height)
        assertEquals(128, tile.appIcon.width)
        assertEquals(29f, tile.appLabel.textSize, 0.1f)
        assertTrue(tile.appLabel.bottom <= tile.root.height - tile.root.paddingBottom)
        assertTrue(AppGrid.tileHeight(context(), 1008) > height)
    }

    @Test @Config(qualifiers = "w1080dp-h1920dp-port-mdpi")
    fun tightHomeAreaNeverCutsTheTileContentEvenWithLargerSystemFont() {
        val app = RuntimeEnvironment.getApplication()
        val config = Configuration(app.resources.configuration).apply { fontScale = 1.6f }
        val large = ContextThemeWrapper(app.createConfigurationContext(config), R.style.Theme_RadioLauncher)
        val tile = ItemAppBinding.inflate(LayoutInflater.from(large))
        tile.appLabel.text = "车辆控制与能量管理"
        measure(tile.root, 336)
        assertTrue(AppGrid.homeTileHeight(large, 1008, 200) >= tile.root.height)
        assertTrue(tile.appLabel.bottom <= tile.root.height - tile.root.paddingBottom)
    }

    @Test
    @Config(qualifiers = "w800dp-h480dp-land-mdpi")
    fun threeShortcutsRemainClickableInsideOneLandscapeRow() {
        val container = LinearLayout(context())
        val selected = mutableListOf<Int>()
        AppGrid.shortcuts(container, Prefs.PANEL_SHORTCUT_COUNT) { index, row ->
            ItemAppBinding.inflate(LayoutInflater.from(row.context), row, false).apply {
                appLabel.text = "快捷入口 $index"
                root.setOnClickListener { selected.add(index) }
            }.root
        }
        measure(container, 276, 188)
        assertEquals(3, container.childCount)
        val first = container.getChildAt(0)
        for (index in 0..2) {
            val cell = container.getChildAt(index)
            assertEquals(first.top, cell.top)
            assertEquals(first.width, cell.width)
            val icon = cell.findViewById<View>(R.id.appIcon)
            assertTrue(icon.right <= cell.width)
            cell.performClick()
        }
        assertEquals(listOf(0, 1, 2), selected)
    }

    @Test
    @Config(qualifiers = "w1080dp-h1920dp-port-mdpi")
    fun singleShortcutRowLeavesRoomForAnotherFullApplicationRow() {
        val container = LinearLayout(context())
        AppGrid.shortcuts(container, Prefs.PANEL_SHORTCUT_COUNT) { _, row ->
            ItemAppBinding.inflate(LayoutInflater.from(row.context), row, false).apply {
                appLabel.text = "车辆控制与能量管理"
            }.root
        }
        measure(container, 1008)
        assertEquals(3, container.childCount)
        val tile = AppGrid.tileHeight(context(), 1008)
        // wm3 used two 240dp shortcut rows. The larger wm4 text plus
        // tighter empty spacing still gives the drawer one complete tile.
        assertTrue("Shortcut height ${container.height}, tile $tile", 480 - container.height >= tile)
        val prefs = Prefs(context())
        prefs.setCardShortcut(Prefs.SLOT_SECOND, 0, "first")
        prefs.setCardShortcut(Prefs.SLOT_SECOND, 3, "previous-fourth")
        assertEquals("first", Prefs(context()).cardShortcut(Prefs.SLOT_SECOND, 0))
        assertEquals("previous-fourth", Prefs(context()).cardShortcut(Prefs.SLOT_SECOND, 3))
    }
}
