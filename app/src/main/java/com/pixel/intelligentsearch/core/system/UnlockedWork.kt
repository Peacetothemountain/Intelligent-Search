package com.pixel.intelligentsearch.core.system

import android.content.Context
import androidx.core.os.UserManagerCompat
import androidx.work.WorkManager

/**
 * WorkManager, or null while the user hasn't unlocked the phone since it started. WorkManager keeps its queue in
 * normal (credential-protected) storage, which can't be opened before that first unlock, and starting it then
 * crashes the app on a background thread it can't catch. The widgets draw before the unlock, so their scheduling
 * goes through here and is skipped then; [com.pixel.intelligentsearch.App] arranges the same work again once the user
 * unlocks.
 */
fun Context.workManagerOnceUnlocked(): WorkManager? =
    if (UserManagerCompat.isUserUnlocked(this)) WorkManager.getInstance(applicationContext) else null
