package com.pixel.intelligentsearch.core.ui

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap

/**
 * An LRU cache bounded by memory rather than entry count, so a run of large icons can't outgrow the heap.
 * The budget is 1/[heapDivisor] of this app's maximum heap, measured in KiB.
 */
fun <K : Any, V : Any> memoryLruCache(heapDivisor: Int, bytesOf: (V) -> Int): LruCache<K, V> {
    val budgetKiB = (Runtime.getRuntime().maxMemory() / 1024 / heapDivisor).toInt().coerceAtLeast(1024)
    return object : LruCache<K, V>(budgetKiB) {
        override fun sizeOf(key: K, value: V): Int = (bytesOf(value) / 1024).coerceAtLeast(1)
    }
}

fun <K : Any> bitmapLruCache(heapDivisor: Int): LruCache<K, Bitmap> =
    memoryLruCache(heapDivisor) { it.allocationByteCount }

fun <K : Any> imageBitmapLruCache(heapDivisor: Int): LruCache<K, ImageBitmap> =
    memoryLruCache(heapDivisor) { it.asAndroidBitmap().allocationByteCount }
