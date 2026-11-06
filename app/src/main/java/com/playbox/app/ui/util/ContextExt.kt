package com.playbox.app.ui.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/**
 * Compose 的 LocalContext 可能是 ContextThemeWrapper，需要沿着包装链找到 Activity。
 */
tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
