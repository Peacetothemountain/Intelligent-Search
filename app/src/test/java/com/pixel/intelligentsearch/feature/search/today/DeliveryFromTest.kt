package com.pixel.intelligentsearch.feature.search.today

import android.app.Notification
import com.pixel.intelligentsearch.core.glance.IndexedNotification
import org.junit.Assert.assertEquals
import java.time.ZoneOffset
import org.junit.Test

/** How the package tile reads shipping notifications: "delivered" counts only once it has happened. */
class DeliveryFromTest {

    private fun stepOf(text: String): Int? =
        deliveryFrom(listOf(IndexedNotification("k", "com.shop", "Shop", "Order update", text, NOW - 60_000L)), NOW, ZONE)?.step

    @Test
    fun eachPhrasingReadsAsItsStep() {
        mapOf(
            "Your order has shipped" to 1,
            "Your package will be delivered tomorrow" to 1,
            "Your package is out for delivery" to 2,
            "Your package will be delivered today" to 2,
            "Out for delivery: will be delivered by 8 PM" to 2,
            "Your package is delayed" to 1, "Your delivery is running late" to 1, "Your delivery has been rescheduled" to 1,
            "Delayed, now arriving today" to 2,
            "Your package was delivered" to 3,
            "Delivered: your order is at the front door" to 3,
            "Your package could not be delivered" to null,
            "Your order wasn't delivered" to null
        ).forEach { (text, step) -> assertEquals(text, step, stepOf(text)) }
    }

    @Test
    fun aFailedDeliveryHidesTheOlderNewsForIt() {
        val older = IndexedNotification("a", "com.shop", "Shop", "Your package", "Out for delivery", NOW - 3_600_000L)
        listOf(
            "Your package could not be delivered", "Delivery attempted: no one was home",
            "We were unable to deliver your package", "Delivery exception: address problem",
            "Your package is being returned to sender"
        ).forEach { text ->
            val newer = IndexedNotification("b", "com.shop", "Shop", "Your package", text, NOW - 60_000L)
            assertEquals(text, null, deliveryFrom(listOf(older, newer), NOW, ZONE))
        }
        // A carrier's own words, with no "your package" in them, are news too.
        val carrierWords = IndexedNotification("c", "com.carrier", "Carrier", "We missed you!", "Delivery attempted today", NOW - 60_000L)
        assertEquals(null, deliveryFrom(listOf(older, carrierWords), NOW, ZONE))
        val outToday = IndexedNotification("d", "com.carrier", "Carrier", "Update", "Out for delivery today", NOW - 60_000L)
        assertEquals(outToday, deliveryFrom(listOf(outToday), NOW, ZONE)?.entry)
    }

    @Test
    fun outForDeliveryHoldsOnlyOnTheDayItWasPosted() {
        // NOW is 13:46 UTC: two hours back is this morning, a day back is yesterday.
        fun stepPostedAgo(ms: Long) =
            deliveryFrom(listOf(IndexedNotification("k", "com.shop", "Shop", "Your package", "Out for delivery", NOW - ms)), NOW, ZONE)?.step
        assertEquals(2, stepPostedAgo(2 * 3_600_000L))
        assertEquals(null, stepPostedAgo(24 * 3_600_000L))
    }

    @Test
    fun aChatThatSaysOrderIsNotPackageNews() {
        val carrier = IndexedNotification("a", "com.shop", "Shop", "Your package", "Your order has shipped", NOW - 3_600_000L)
        val chat = IndexedNotification("b", "com.chat", "Chat", "Sam", "On the way home, I'll order pizza", NOW - 60_000L, Notification.CATEGORY_MESSAGE)
        assertEquals(carrier, deliveryFrom(listOf(carrier, chat), NOW, ZONE)?.entry)
        // A carrier's own text message is still package news.
        val text = IndexedNotification("c", "com.sms", "Messages", "USPS", "Your package was delivered", NOW - 30_000L, Notification.CATEGORY_MESSAGE)
        assertEquals(text, deliveryFrom(listOf(carrier, chat, text), NOW, ZONE)?.entry)
    }

    @Test
    fun aShopsOfferIsNotPackageNews() {
        val shipped = IndexedNotification("a", "com.shop", "Shop", "Your package", "Your order has shipped", NOW - 3_600_000L)
        listOf(
            "Free delivery today on orders over \$15", "20% off your next order, delivered today", "Deals on delivery today",
            "Use promo code EATS10 on your order", "Save on delivery today", "Big sale: orders arriving today"
        ).forEach { text ->
            val offer = IndexedNotification("b", "com.delivery", "Delivery", "Hungry?", text, NOW - 60_000L)
            assertEquals(text, shipped, deliveryFrom(listOf(shipped, offer), NOW, ZONE)?.entry)
        }
        val promo = IndexedNotification("c", "com.delivery", "Delivery", "Tonight", "Order now for delivery today", NOW - 60_000L, Notification.CATEGORY_PROMO)
        assertEquals(shipped, deliveryFrom(listOf(shipped, promo), NOW, ZONE)?.entry)
        // Nor is one in no offer's words, and it never hides the older news.
        listOf("Order now for delivery today", "Dinner, delivered. Order now").forEach { text ->
            val ad = IndexedNotification("f", "com.delivery", "Delivery", "Tonight", text, NOW - 60_000L)
            assertEquals(text, shipped, deliveryFrom(listOf(shipped, ad), NOW, ZONE)?.entry)
        }
        // A shop's headline names the item, whatever its words.
        mapOf(
            "Arriving today: Deal of the Day Lamp" to ("Your package will arrive today" to 2),
            "Shipped: Garage Sale Signs" to ("Track your package" to 1)
        ).forEach { (title, update) ->
            val news = IndexedNotification("g", "com.shop", "Shop", title, update.first, NOW - 60_000L)
            assertEquals(title, update.second, deliveryFrom(listOf(news), NOW, ZONE)?.step)
        }
        // A real update with an offer under it is still the package's news.
        val update = IndexedNotification("d", "com.shop", "Shop", "Shop", "Your order has shipped! 10% off your next one", NOW - 60_000L)
        assertEquals(1, deliveryFrom(listOf(update), NOW, ZONE)?.step)
        val delivered = IndexedNotification("e", "com.shop", "Shop", "Shop", "Your package was delivered. Rate it for a coupon", NOW - 60_000L)
        assertEquals(3, deliveryFrom(listOf(delivered), NOW, ZONE)?.step)
    }

    @Test
    fun borderIsNotAnOrder() {
        val trip = IndexedNotification("k", "com.maps", "Maps", "Trip", "At the border, on the way", NOW - 60_000L)
        assertEquals(null, deliveryFrom(listOf(trip), NOW, ZONE))
    }

    private companion object {
        const val NOW = 1_000_000_000L
        val ZONE: ZoneOffset = ZoneOffset.UTC
    }
}
