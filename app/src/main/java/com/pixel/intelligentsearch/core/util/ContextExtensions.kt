package com.pixel.intelligentsearch.core.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/** The [Activity] this context belongs to, unwrapping [ContextWrapper]s, or null for non-UI contexts. */
tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
