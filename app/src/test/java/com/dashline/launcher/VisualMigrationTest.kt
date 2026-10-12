// SPDX-License-Identifier: GPL-3.0-only
package com.dashline.launcher

import android.content.Context
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23])
class VisualMigrationTest {
    @Test fun selectedStylePreservesExistingLauncherConfiguration() {
        val context: Context = RuntimeEnvironment.getApplication()
        val store = context.getSharedPreferences("radio_launcher", 0)
        store.edit().clear().putBoolean("wm_profile_v1", true)
            .putBoolean("cards_migrated", true).putBoolean("setup_done", true)
            .putString("nav_app", "com.example.navigation")
            .putString("card_second_shortcut_0", "com.example.favorite")
            .putStringSet("hidden_apps", setOf("com.example.hidden"))
            .putString("wm_orientation", "portrait").putString("gradient", "midnight")
            .putString("theme_mode", "light").commit()
        val prefs = Prefs(context)
        assertEquals("wm_ice", prefs.gradient)
        assertEquals(Prefs.THEME_DARK, prefs.themeMode)
        assertTrue(prefs.setupDone)
        assertEquals("com.example.navigation", prefs.navApp)
        assertEquals("com.example.favorite", prefs.cardShortcut(Prefs.SLOT_SECOND, 0))
        assertEquals(setOf("com.example.hidden"), prefs.hiddenApps)
        assertEquals("portrait", store.getString("wm_orientation", ""))
    }

    @Test fun laterThemeChoicesAreNotOverwrittenOnReopeningSettings() {
        val context: Context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("radio_launcher", 0).edit().clear().commit()
        Prefs(context).apply { gradient = "sunset"; themeMode = Prefs.THEME_LIGHT }
        val reopened = Prefs(context)
        assertEquals("sunset", reopened.gradient)
        assertEquals(Prefs.THEME_LIGHT, reopened.themeMode)
    }
}
