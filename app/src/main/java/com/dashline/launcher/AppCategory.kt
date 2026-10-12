// SPDX-License-Identifier: GPL-3.0-only
package com.dashline.launcher

/** The same partition is used on the home page and in the application drawer. */
internal enum class AppCategory {
    THIRD_PARTY, FACTORY, ALL;

    fun includes(app: AppInfo): Boolean = when (this) {
        THIRD_PARTY -> !app.isSystem && !app.isFactory
        FACTORY -> app.isSystem || app.isFactory
        ALL -> true
    }
}
