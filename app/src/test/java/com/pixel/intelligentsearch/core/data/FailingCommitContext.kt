package com.pixel.intelligentsearch.core.data

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences

/**
 * A context whose SharedPreferences report every commit() as failed while [failCommits] is set. Like the real
 * implementation when the disk write fails, the new values still reach memory: only the result says false.
 */
class FailingCommitContext(base: Context) : ContextWrapper(base) {

    @Volatile
    var failCommits = false

    override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences =
        FailingCommitPrefs(super.getSharedPreferences(name, mode))

    private inner class FailingCommitPrefs(private val prefs: SharedPreferences) : SharedPreferences by prefs {
        override fun edit(): SharedPreferences.Editor = FailingCommitEditor(prefs.edit())
    }

    // Every setter returns this wrapper, so a chained commit() still comes here.
    private inner class FailingCommitEditor(private val editor: SharedPreferences.Editor) : SharedPreferences.Editor by editor {
        override fun putString(key: String?, value: String?): SharedPreferences.Editor { editor.putString(key, value); return this }
        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor { editor.putStringSet(key, values); return this }
        override fun putInt(key: String?, value: Int): SharedPreferences.Editor { editor.putInt(key, value); return this }
        override fun putLong(key: String?, value: Long): SharedPreferences.Editor { editor.putLong(key, value); return this }
        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor { editor.putFloat(key, value); return this }
        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor { editor.putBoolean(key, value); return this }
        override fun remove(key: String?): SharedPreferences.Editor { editor.remove(key); return this }
        override fun clear(): SharedPreferences.Editor { editor.clear(); return this }
        override fun commit(): Boolean = editor.commit() && !failCommits
    }
}
