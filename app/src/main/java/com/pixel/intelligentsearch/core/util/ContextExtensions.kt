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

/**
 * Where app files can be read right now: normal (credential-protected) storage once the user has unlocked
 * since boot, device-protected storage before that. Data that must never sit in device-protected storage
 * (secrets, private lists) should use the normal context and fail while locked instead.
 */
fun Context.storageContext(): Context {
    if (isDeviceProtectedStorage) return this
    val unlocked = getSystemService(android.os.UserManager::class.java)?.isUserUnlocked ?: true
    return if (unlocked) this else createDeviceProtectedStorageContext()
}
