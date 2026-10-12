// SPDX-License-Identifier: GPL-3.0-only
package com.dashline.launcher

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.SwitchCompat

/** The enabled policy is permanent; clicking still requests a repair. */
class WmEnabledSwitch @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    SwitchCompat(context, attrs) {
    override fun setChecked(checked: Boolean) = super.setChecked(true)
    override fun toggle() { isChecked = true }
}
