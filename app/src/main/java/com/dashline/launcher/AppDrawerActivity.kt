// Copyright (C) 2026 Metehan Kaygısız
// SPDX-License-Identifier: GPL-3.0-only

package com.dashline.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ActivityNotFoundException
import android.net.Uri
import android.provider.Settings
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.GridLayoutManager
import com.dashline.launcher.databinding.ActivityAppDrawerBinding

/** Grid of every launchable app, with search, a frequently-used strip, and hiding. */
class AppDrawerActivity : BaseActivity() {

    private lateinit var binding: ActivityAppDrawerBinding
    private lateinit var prefs: Prefs
    private lateinit var adapter: AppAdapter
    private lateinit var frequentAdapter: AppAdapter

    private var allApps: List<AppInfo> = emptyList()
    private var category = AppCategory.THIRD_PARTY

    // Refresh the list when apps are installed/removed while the drawer is open.
    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = reload()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppDrawerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = Prefs(this)

        binding.header.pageTitle.setText(R.string.all_apps)
        bindPageNav(binding.header.navBack, binding.header.navHome)

        adapter = AppAdapter(emptyList(), onClick = { launch(it) }, onLongClick = { showAppActions(it) })
        binding.appGrid.layoutManager = GridLayoutManager(this, AppGrid.COLUMNS)
        binding.appGrid.adapter = adapter
        binding.appGrid.setHasFixedSize(true)

        frequentAdapter = AppAdapter(emptyList(), onClick = { launch(it) },
            onLongClick = { showAppActions(it) })
        binding.frequentGrid.layoutManager = GridLayoutManager(this, AppGrid.COLUMNS)
        binding.frequentGrid.adapter = frequentAdapter
        binding.filterAll.setOnClickListener { selectCategory(AppCategory.ALL) }
        binding.filterThirdParty.setOnClickListener { selectCategory(AppCategory.THIRD_PARTY) }
        binding.filterFactory.setOnClickListener { selectCategory(AppCategory.FACTORY) }

        binding.searchBox.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) = applyFilter(s?.toString().orEmpty())
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })

        reload()
    }

    private fun reload() {
        allApps = AppRepository.loadLaunchableApps(this, prefs.hiddenApps)

        val byPackage = allApps.associateBy { it.packageName }
        val frequent = Usage.topApps(this, FREQUENT_COUNT).mapNotNull { byPackage[it] }
        frequentAdapter.update(frequent)

        applyFilter(binding.searchBox.text?.toString().orEmpty())
    }

    private fun selectCategory(selected: AppCategory) {
        category = selected
        applyFilter(binding.searchBox.text.toString())
        binding.appGrid.scrollToPosition(0)
    }

    private fun applyFilter(query: String) {
        val q = query.trim().lowercase()
        val filtered = allApps.filter { app ->
            category.includes(app) &&
                (q.isEmpty() || app.label.lowercase().contains(q) || app.packageName.lowercase().contains(q))
        }
        listOf(binding.filterThirdParty to AppCategory.THIRD_PARTY,
            binding.filterFactory to AppCategory.FACTORY,
            binding.filterAll to AppCategory.ALL).forEach { (button, value) ->
            button.isSelected = category == value
            button.alpha = 1f
            button.setTextColor(if (category == value) accentColor()
                else androidx.core.content.ContextCompat.getColor(this, R.color.text_primary))
        }
        adapter.update(filtered)

        // Frequent strip only makes sense when not searching.
        val showFrequent = q.isEmpty() && category == AppCategory.ALL && frequentAdapter.itemCount > 0
        binding.frequentHeader.visibility = if (showFrequent) View.VISIBLE else View.GONE
        binding.frequentGrid.visibility = if (showFrequent) View.VISIBLE else View.GONE
        binding.emptyState.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun launch(app: AppInfo) {
        if (!AppRepository.launch(this, app.packageName)) {
            Toast.makeText(
                this, getString(R.string.couldnt_launch, app.label), Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun showAppActions(app: AppInfo) {
        val actions = arrayOf(getString(R.string.hide_app), getString(R.string.app_details),
            getString(R.string.uninstall_app))
        val dialog = AlertDialog.Builder(this)
            .setTitle(app.label)
            .setAdapter(ArrayAdapter(this, R.layout.item_app_action, actions)) { _, position ->
                when (position) {
                    0 -> {
                        prefs.hideApp(app.packageName)
                        reload()
                    }
                    1 -> openAppSystemPage(app, Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    2 -> openAppSystemPage(app, Intent.ACTION_UNINSTALL_PACKAGE)
                }
            }
            .show()
        dialog.findViewById<TextView>(androidx.appcompat.R.id.alertTitle)?.apply {
            textSize = 32f
            maxLines = 3
        }
    }

    private fun openAppSystemPage(app: AppInfo, action: String) {
        val intent = Intent(action, Uri.fromParts("package", app.packageName, null))
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.app_system_page_unavailable, Toast.LENGTH_LONG).show()
        } catch (e: SecurityException) {
            Toast.makeText(this, R.string.app_system_page_unavailable, Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        registerReceiver(packageReceiver, filter)
        reload()
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(packageReceiver)
        } catch (e: IllegalArgumentException) {
            // wasn't registered; ignore
        }
    }

    private companion object {
        const val FREQUENT_COUNT = AppGrid.COLUMNS
    }
}
