// SPDX-License-Identifier: GPL-3.0-only
// WM adaptation, 2026-10-10. Components verified against the supplied backup.
package com.dashline.launcher

import android.content.Context

data class FactoryEntry(val id: String, val label: String, val packageName: String, val activity: String) {
    val key: String get() = "$packageName/$activity"
}

object WmFactory {
    val entries = listOf(
        FactoryEntry("map", "地图导航", "com.ivi.map", "com.ivi.map.MapAutoActivity"),
        FactoryEntry("music", "音乐", "com.ivi.musicplayer", "com.ivi.musicplayer.ui.home.HomePageActivity"),
        FactoryEntry("radio", "收音机", "com.ivi.radio", "com.ivi.radio.view.activity.HomeActivity"),
        FactoryEntry("phone", "电话", "com.yftech.btphone", "com.yftech.btphone.activity.MainActivity"),
        FactoryEntry("vehicle", "车辆控制", "com.yftech.vehiclecenter", "com.yftech.vehiclecenter.ui.activites.MainActivity"),
        FactoryEntry("energy", "能量管理", "com.yftech.vehiclecenter", "com.yftech.vehiclecenter.ui.activites.EnergyActivity"),
        FactoryEntry("avm", "全景环视", "com.yftech.avmservice", "com.yftech.avmservice.MainActivity"),
        FactoryEntry("dvr", "行车记录仪", "com.yftech.dvr", "com.yftech.dvr.main.MainActivity"),
        FactoryEntry("maint", "车辆养修", "com.ivi.maint", "com.ivi.maint.module.home.view.activity.HomeActivity"),
        FactoryEntry("settings", "系统设置", "com.android.settings", "com.android.settings.Settings"),
        FactoryEntry("audiobook", "喜马拉雅", "com.ivi.audiobook", "com.ivi.audiobook.uimodule.module.homepager.view.HomePagerActivity"),
        FactoryEntry("video", "本地视频", "com.yftech.video", "com.yftech.video.activity.MainActivity"),
        FactoryEntry("netvideo", "爱奇艺", "com.ivi.netvideo", "com.ivi.netvideo.module.home.view.activity.SplashActivity"),
        FactoryEntry("wechat", "车载通讯", "com.ivi.wechat", "com.ivi.wechat.ui.EntryActivity"),
        FactoryEntry("mijia", "车家互联", "com.ivi.mijia", "com.ivi.mijia.activity.StartActivity"),
        FactoryEntry("relationship", "威觅", "com.living.relationship", "com.living.relationship.module.home.view.HomeActivity"),
        FactoryEntry("store", "主题商店", "com.living.store", "com.living.store.ui.activity.MainActivity"),
        FactoryEntry("xxqg", "学习强国", "com.wm.xxqg", "com.wm.xxqg.base.activity.LauncherAcitity"),
        FactoryEntry("original_home", "返回原厂桌面", "com.wm.launcher", "com.wm.launcher.launcher2.Launcher2")
    )
    fun target(context: Context, entry: FactoryEntry): String =
        context.getSharedPreferences("radio_launcher", Context.MODE_PRIVATE)
            .getString("wm_factory_${entry.id}", entry.key) ?: entry.key

    /** Function artwork only for exact factory entries; installed app logos stay intact. */
    fun iconRes(key: String): Int? = when (entries.firstOrNull { it.key == key }?.id) {
        "vehicle" -> R.drawable.wm_ic_vehicle
        "energy" -> R.drawable.wm_ic_energy
        "avm" -> R.drawable.wm_ic_surround
        else -> null
    }
}
