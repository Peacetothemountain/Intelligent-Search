package com.pixel.intelligentsearch.core.util

/**
 * [coerceIn] for bounds computed from a size, which can cross while a view is tiny (mid-animation, split
 * screen). coerceIn throws IllegalArgumentException when [max] < [min]; that crashed drawing code in
 * production. Here the range collapses to [min] instead.
 */
fun Float.clampSafely(min: Float, max: Float): Float = coerceIn(min, maxOf(min, max))
