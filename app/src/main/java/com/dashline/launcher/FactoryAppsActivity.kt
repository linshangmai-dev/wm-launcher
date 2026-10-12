// SPDX-License-Identifier: GPL-3.0-only
package com.dashline.launcher

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.dashline.launcher.databinding.ActivityFactoryAppsBinding
import com.dashline.launcher.databinding.ItemFactoryAppBinding

class FactoryAppsActivity : BaseActivity() {
    private lateinit var binding: ActivityFactoryAppsBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFactoryAppsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.header.pageTitle.setText(R.string.wm_factory_apps)
        bindPageNav(binding.header.navBack, binding.header.navHome)
        binding.factoryGrid.layoutManager = GridLayoutManager(this, AppGrid.COLUMNS)
        binding.factoryGrid.adapter = FactoryAdapter()
    }
    override fun onResume() {
        super.onResume()
        if (::binding.isInitialized) binding.factoryGrid.adapter?.notifyDataSetChanged()
    }
    private fun choose(entry: FactoryEntry) = AppPickerActivity.start(this, "factory:${entry.id}", 701)
    private inner class FactoryAdapter : RecyclerView.Adapter<FactoryHolder>() {
        // The xxqg entry becomes this desktop in the entry-compatible variant.
        private val entries = WmFactory.entries.filter { it.packageName != packageName }
        override fun getItemCount() = entries.size
        override fun onCreateViewHolder(parent: ViewGroup, type: Int): FactoryHolder =
            FactoryHolder(ItemFactoryAppBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        override fun onBindViewHolder(holder: FactoryHolder, position: Int) {
            val entry = entries[position]
            val target = WmFactory.target(this@FactoryAppsActivity, entry)
            val available = AppRepository.isInstalled(this@FactoryAppsActivity, target)
            holder.view.factoryLabel.text = entry.label
            holder.view.factoryStatus.setText(if (available) R.string.wm_available else R.string.wm_unavailable)
            holder.view.factoryIcon.setImageDrawable(AppRepository.iconFor(this@FactoryAppsActivity, target))
            if (holder.view.factoryIcon.drawable == null) holder.view.factoryIcon.setImageResource(R.drawable.ic_apps)
            holder.view.factoryIcon.clearColorFilter()
            WmFactory.iconRes(target)?.let { icon ->
                holder.view.factoryIcon.setImageResource(icon)
                holder.view.factoryIcon.setColorFilter(accentColor(), android.graphics.PorterDuff.Mode.SRC_IN)
            }
            holder.view.root.alpha = if (available) 1f else 0.60f
            holder.view.root.setOnClickListener {
                if (!AppRepository.launch(this@FactoryAppsActivity, target)) {
                    AlertDialog.Builder(this@FactoryAppsActivity).setTitle(entry.label)
                        .setMessage(R.string.wm_missing_message)
                        .setPositiveButton(R.string.wm_choose_replacement) { _, _ -> choose(entry) }
                        .setNegativeButton(android.R.string.cancel, null).show()
                }
            }
            holder.view.root.setOnLongClickListener {
                AlertDialog.Builder(this@FactoryAppsActivity).setTitle(entry.label)
                    .setItems(arrayOf(getString(R.string.wm_choose_replacement), getString(R.string.wm_reset_entry))) { _, which ->
                        if (which == 0) choose(entry) else {
                            getSharedPreferences("radio_launcher", MODE_PRIVATE).edit()
                                .remove("wm_factory_${entry.id}").apply()
                            notifyItemChanged(position)
                        }
                    }.show()
                true
            }
        }
    }
    private class FactoryHolder(val view: ItemFactoryAppBinding) : RecyclerView.ViewHolder(view.root)
}
