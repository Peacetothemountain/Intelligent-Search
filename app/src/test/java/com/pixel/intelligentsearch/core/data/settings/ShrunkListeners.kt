package com.pixel.intelligentsearch.core.data.settings

import android.content.SharedPreferences
import java.lang.ref.WeakReference

/**
 * Does to [owner] what the release build's shrinker can do: a field that only holds a settings change listener is
 * never read, so R8 removes it, and SharedPreferences keeps its listeners weakly. Every such field is cleared and the
 * garbage collector run until the listeners it held are gone, so a test then sees only what still works without them.
 */
fun forgetHeldListeners(owner: Any) {
    val dropped = ArrayList<WeakReference<Any>>()
    var type: Class<*>? = owner.javaClass
    while (type != null && type != Any::class.java) {
        for (field in type.declaredFields) {
            if (!SharedPreferences.OnSharedPreferenceChangeListener::class.java.isAssignableFrom(field.type)) continue
            field.isAccessible = true
            field.get(owner)?.let { dropped += WeakReference(it) }
            field.set(owner, null)
        }
        type = type.superclass
    }
    repeat(50) {
        if (dropped.all { it.get() == null }) return
        System.gc()
        Thread.sleep(10)
    }
    check(dropped.all { it.get() == null }) { "a dropped listener is still reachable" }
}
