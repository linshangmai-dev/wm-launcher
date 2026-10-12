// SPDX-License-Identifier: GPL-3.0-only
package com.dashline.launcher

import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog

/** Enlarge this settings page and its dialogs once, including lazily created list rows. */
internal object SettingsTextSizing {
    fun enlarge(root: View) {
        if (root.getTag(R.id.wm_settings_text_enlarged) != null) return
        root.setTag(R.id.wm_settings_text_enlarged, true)
        if (root is TextView) {
            root.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                root.textSize + 4f * root.resources.displayMetrics.scaledDensity)
        }
        if (root is ViewGroup) {
            for (index in 0 until root.childCount) enlarge(root.getChildAt(index))
            root.setOnHierarchyChangeListener(object : ViewGroup.OnHierarchyChangeListener {
                override fun onChildViewAdded(parent: View?, child: View?) {
                    child?.let { enlarge(it) }
                }
                override fun onChildViewRemoved(parent: View?, child: View?) = Unit
            })
        }
    }

    fun show(builder: AlertDialog.Builder): AlertDialog = builder.create().apply {
        show()
        window?.decorView?.let { enlarge(it) }
    }
}
