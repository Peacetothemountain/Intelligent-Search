package com.pixel.intelligentsearch.feature.search.today

import com.pixel.intelligentsearch.core.glance.IndexedNotification
import org.junit.Assert.assertEquals
import org.junit.Test

/** How the package tile reads shipping notifications: "delivered" counts only once it has happened. */
class DeliveryFromTest {

    private fun stepOf(text: String): Int? =
        deliveryFrom(listOf(IndexedNotification("k", "com.shop", "Shop", "Order update", text, NOW - 60_000L)), NOW)?.step

    @Test
    fun eachPhrasingReadsAsItsStep() {
        mapOf(
            "Your order has shipped" to 1,
            "Your package will be delivered tomorrow" to 1,
            "Your package is out for delivery" to 2,
            "Your package will be delivered today" to 2,
            "Out for delivery: will be delivered by 8 PM" to 2,
            "Your package was delivered" to 3,
            "Delivered: your order is at the front door" to 3,
            "Your package could not be delivered" to null,
            "Your order wasn't delivered" to null
        ).forEach { (text, step) -> assertEquals(text, step, stepOf(text)) }
    }

    @Test
    fun aFailedDeliveryHidesTheOlderNewsForIt() {
        val older = IndexedNotification("a", "com.shop", "Shop", "Your package", "Out for delivery", NOW - 3_600_000L)
        val newer = IndexedNotification("b", "com.shop", "Shop", "Your package", "Your package could not be delivered", NOW - 60_000L)
        assertEquals(null, deliveryFrom(listOf(older, newer), NOW))
    }

    private companion object {
        const val NOW = 1_000_000_000L
    }
}
