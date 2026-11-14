package com.pixel.intelligentsearch.core.answers

import android.app.usage.StorageStatsManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.storage.StorageManager
import java.net.Inet4Address
import java.net.Inet6Address
import java.util.Locale

/**
 * Answers about this phone, read on the device with nothing sent anywhere:
 * - "storage", "free space", "how much storage do I have": how much space is free, of how much
 * - "my ip", "ip address", "what's my ip": the phone's address on the network it is on
 *
 * Only these whole phrasings are answered, so a search that merely contains "storage" or "ip" is left alone.
 * [storage] and [network] read the phone; tests hand in fixed readings.
 */
class PhoneAnswers(
    private val storage: () -> StorageReading?,
    private val network: () -> NetworkReading?
) {
    constructor(context: Context) : this({ readStorage(context) }, { readNetwork(context) })

    enum class Kind(val iconType: String) { STORAGE("storage"), ADDRESS("ip") }

    /** An answer: [title] for the card's large line, [subtitle] under it. [copyable] is the text a tap copies, if any. */
    data class PhoneAnswer(val kind: Kind, val title: String, val subtitle: String, val copyable: String? = null)

    /** The phone's own storage: [freeBytes] still free of [totalBytes]. */
    data class StorageReading(val totalBytes: Long, val freeBytes: Long)

    /** The address the phone has on the network it is using, and whether that network is [wifi], [mobile] or another kind. */
    data class NetworkReading(val address: String, val wifi: Boolean, val mobile: Boolean)

    /** The answer to [rawQuery], or null when it doesn't ask about the phone or the phone can't say. */
    fun evaluate(rawQuery: String): PhoneAnswer? {
        val query = normalize(rawQuery)
        return when (query) {
            in STORAGE_PHRASES -> storage()?.let(::storageAnswer)
            in ADDRESS_PHRASES -> addressAnswer(network())
            else -> null
        }
    }

    companion object {
        internal val STORAGE_PHRASES = setOf(
            "storage", "phone storage", "storage space", "storage left", "free storage", "free space", "space left",
            "disk space", "how much storage", "how much space", "how much storage do i have", "how much space do i have",
            "how much storage is left", "how much space is left", "how much storage do i have left",
            "how much space do i have left", "how much free space do i have", "how much storage is free"
        )
        internal val ADDRESS_PHRASES = setOf(
            "ip", "my ip", "ip address", "my ip address", "local ip", "local ip address", "wifi ip", "wi-fi ip",
            "what is my ip", "whats my ip", "what is my ip address", "whats my ip address", "show my ip", "show my ip address"
        )

        /** Lowercase, apostrophes dropped ("what's" is "whats"), spaces collapsed and a closing "?" or "." removed. */
        internal fun normalize(rawQuery: String): String =
            rawQuery.lowercase(Locale.ROOT).replace("'", "").replace("’", "").trim().trimEnd('?', '.', '!').trim()
                .replace(Regex("\\s+"), " ")

        internal fun storageAnswer(reading: StorageReading): PhoneAnswer? {
            if (reading.totalBytes <= 0L) return null
            val free = reading.freeBytes.coerceIn(0L, reading.totalBytes)
            val usedPercent = ((reading.totalBytes - free) * 100 / reading.totalBytes).toInt()
            return PhoneAnswer(
                Kind.STORAGE,
                title = "${formatSize(free)} free",
                subtitle = "of ${formatSize(reading.totalBytes)} · $usedPercent% used"
            )
        }

        internal fun addressAnswer(reading: NetworkReading?): PhoneAnswer =
            if (reading == null) {
                PhoneAnswer(Kind.ADDRESS, "Not connected", "This phone has no network address right now")
            } else {
                val where = when {
                    reading.wifi -> "This phone's address on Wi-Fi"
                    reading.mobile -> "This phone's address on mobile data"
                    else -> "This phone's address on this network"
                }
                PhoneAnswer(Kind.ADDRESS, reading.address, "$where · Tap to copy", copyable = reading.address)
            }

        /** Decimal sizes, as Android's Settings writes storage: "118 GB", "9.4 GB", "512 MB". */
        internal fun formatSize(bytes: Long): String {
            val gb = bytes / 1_000_000_000.0
            return when {
                gb >= 10 -> String.format(Locale.US, "%.0f GB", gb)
                gb >= 1 -> String.format(Locale.US, "%.1f GB", gb)
                else -> String.format(Locale.US, "%.0f MB", bytes / 1_000_000.0)
            }
        }

        /** The phone's built-in storage as Settings counts it. Null when the phone won't say. */
        private fun readStorage(context: Context): StorageReading? = try {
            val stats = context.getSystemService(StorageStatsManager::class.java)
            StorageReading(
                totalBytes = stats.getTotalBytes(StorageManager.UUID_DEFAULT),
                freeBytes = stats.getFreeBytes(StorageManager.UUID_DEFAULT)
            )
        } catch (_: Throwable) {
            null
        }

        /**
         * The address on the network in use: the usual four-number kind when there is one, else the longer kind that
         * reaches beyond this link. Null with no network.
         */
        private fun readNetwork(context: Context): NetworkReading? = try {
            val connectivity = context.getSystemService(ConnectivityManager::class.java)
            val active = connectivity.activeNetwork
            val addresses = connectivity.getLinkProperties(active)?.linkAddresses.orEmpty().map { it.address }
            val address = addresses.firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
                ?: addresses.firstOrNull { it is Inet6Address && !it.isLinkLocalAddress && !it.isLoopbackAddress }
            val capabilities = connectivity.getNetworkCapabilities(active)
            address?.hostAddress?.let {
                NetworkReading(
                    address = it,
                    wifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true,
                    mobile = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
                )
            }
        } catch (_: Throwable) {
            null
        }
    }
}
