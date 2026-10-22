package com.pixel.intelligentsearch.core.answers

import androidx.compose.runtime.Immutable
import com.pixel.intelligentsearch.core.net.HttpsGet
import com.pixel.intelligentsearch.core.net.fetch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import java.net.URLEncoder
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale

/** The temperature scale answers use: Fahrenheit where people read it (the US and a few others), else Celsius. */
enum class TemperatureUnit(val symbol: String, internal val apiValue: String?) {
    CELSIUS("°C", null),
    FAHRENHEIT("°F", "fahrenheit");

    companion object {
        private val FAHRENHEIT_COUNTRIES = setOf("US", "LR", "MM", "BS", "BZ", "KY", "PW", "FM", "MH")

        fun forLocale(locale: Locale): TemperatureUnit =
            if (locale.country.uppercase(Locale.ROOT) in FAHRENHEIT_COUNTRIES) FAHRENHEIT else CELSIUS
    }
}

/** A place on the map, named as a person would: "Johnson City, Tennessee". */
@Immutable
data class GeoPlace(
    val name: String,
    val region: String?,
    val country: String?,
    val latitude: Double,
    val longitude: Double,
    /** How many people live there, as the geocoder says; 0 when it doesn't. */
    val population: Long = 0
) {
    /** "Johnson City, Tennessee, United States", leaving out parts that repeat the name or are missing. */
    val fullName: String
        get() = listOfNotNull(name, region?.takeIf { it != name }, country?.takeIf { it != name && it != region })
            .joinToString(", ")

    /** "Johnson City, Tennessee": short enough for a card title. */
    val shortName: String
        get() = listOfNotNull(name, (region ?: country)?.takeIf { it != name }).joinToString(", ")
}

/**
 * The home location used for weather answers, stored as one setting: "latitude,longitude;name". Coordinates are kept
 * to two decimals (about a kilometre), all a forecast needs.
 */
object HomeLocationCodec {
    fun encode(place: GeoPlace): String =
        "${coordinate(place.latitude)},${coordinate(place.longitude)};${place.shortName.replace('\n', ' ')}"

    fun decode(stored: String): GeoPlace? {
        val coordinates = stored.substringBefore(';', "")
        val name = stored.substringAfter(';', "").trim()
        val latitude = coordinates.substringBefore(',', "").toDoubleOrNull() ?: return null
        val longitude = coordinates.substringAfter(',', "").toDoubleOrNull() ?: return null
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0 || name.isEmpty()) return null
        return GeoPlace(name, null, null, latitude, longitude)
    }

    /** [value] rounded to two decimals, written the same in every locale. */
    fun coordinate(value: Double): String = String.format(Locale.ROOT, "%.2f", value)
}

/** One hour of a forecast. */
@Immutable
data class WeatherHour(val time: LocalDateTime, val temperature: Double, val code: Int, val isDay: Boolean)

/** One day of a forecast: its overall conditions and its high and low. */
@Immutable
data class WeatherDay(val date: LocalDate, val code: Int, val high: Double, val low: Double)

/** What Open-Meteo said for a place: conditions now (in the place's own time), hour by hour, and day by day. */
@Immutable
data class ForecastData(
    val now: LocalDateTime,
    val temperature: Double,
    val feelsLike: Double?,
    val code: Int,
    val isDay: Boolean,
    val hourly: List<WeatherHour>,
    val daily: List<WeatherDay>
)

/** The weather card: a place's conditions for [day], with the next hours (or the day's hours, for tomorrow). */
@Immutable
data class WeatherReport(
    val placeName: String,
    val unit: TemperatureUnit,
    val day: ForecastDay,
    /** Now, for today; the day's main temperature (its high) for tomorrow. */
    val temperature: Double,
    val feelsLike: Double?,
    val code: Int,
    val isDay: Boolean,
    val high: Double,
    val low: Double,
    val hours: List<WeatherHour>
) {
    companion object {
        /** Hours shown on the card. */
        const val HOURS_SHOWN = 6
        /** Tomorrow's hours shown, every three hours across the day. */
        private val TOMORROW_HOURS = listOf(6, 9, 12, 15, 18, 21)

        /** The report for [day] out of [data], or null when the forecast doesn't reach that day. */
        fun from(data: ForecastData, placeName: String, unit: TemperatureUnit, day: ForecastDay): WeatherReport? {
            val date = if (day == ForecastDay.TODAY) data.now.toLocalDate() else data.now.toLocalDate().plusDays(1)
            val daily = data.daily.firstOrNull { it.date == date } ?: return null
            return if (day == ForecastDay.TODAY) {
                val thisHour = data.now.withMinute(0).withSecond(0).withNano(0)
                WeatherReport(
                    placeName, unit, day, data.temperature, data.feelsLike, data.code, data.isDay, daily.high, daily.low,
                    data.hourly.filter { it.time.isAfter(thisHour) }.take(HOURS_SHOWN)
                )
            } else {
                WeatherReport(
                    placeName, unit, day, daily.high, null, daily.code, true, daily.high, daily.low,
                    data.hourly.filter { it.time.toLocalDate() == date && it.time.hour in TOMORROW_HOURS }
                )
            }
        }
    }
}

/** What WMO weather codes (the ones Open-Meteo reports) mean, in words. */
object WeatherCodes {
    enum class Kind { CLEAR, PARTLY_CLOUDY, CLOUDY, FOG, DRIZZLE, RAIN, FREEZING_RAIN, SNOW, SHOWERS, THUNDERSTORM }

    fun kind(code: Int): Kind = when (code) {
        0, 1 -> Kind.CLEAR
        2 -> Kind.PARTLY_CLOUDY
        3 -> Kind.CLOUDY
        45, 48 -> Kind.FOG
        51, 53, 55 -> Kind.DRIZZLE
        56, 57, 66, 67 -> Kind.FREEZING_RAIN
        61, 63, 65 -> Kind.RAIN
        71, 73, 75, 77, 85, 86 -> Kind.SNOW
        80, 81, 82 -> Kind.SHOWERS
        95, 96, 99 -> Kind.THUNDERSTORM
        else -> Kind.CLOUDY
    }

    fun describe(code: Int): String = when (code) {
        0 -> "Clear"
        1 -> "Mostly Clear"
        2 -> "Partly Cloudy"
        3 -> "Cloudy"
        45 -> "Fog"
        48 -> "Freezing Fog"
        51 -> "Light Drizzle"
        53 -> "Drizzle"
        55 -> "Heavy Drizzle"
        56, 57 -> "Freezing Drizzle"
        61 -> "Light Rain"
        63 -> "Rain"
        65 -> "Heavy Rain"
        66, 67 -> "Freezing Rain"
        71 -> "Light Snow"
        73 -> "Snow"
        75 -> "Heavy Snow"
        77 -> "Snow Grains"
        80 -> "Light Showers"
        81 -> "Showers"
        82 -> "Heavy Showers"
        85, 86 -> "Snow Showers"
        95 -> "Thunderstorms"
        96, 99 -> "Thunderstorms with Hail"
        else -> "Cloudy"
    }
}

/** Reads Open-Meteo's forecast and geocoding answers. Anything malformed reads as null (no answer). */
object OpenMeteoParser {
    const val MAX_BYTES = 128 * 1024

    private val json = Json { ignoreUnknownKeys = true }

    fun parseForecast(body: String): ForecastData? = try {
        val root = json.parseToJsonElement(body) as JsonObject
        val current = root["current"] as JsonObject
        val now = LocalDateTime.parse(current.string("time"))
        val hourlyObj = root["hourly"] as? JsonObject
        val hourly = if (hourlyObj == null) emptyList() else {
            val times = hourlyObj.array("time")
            val temps = hourlyObj.array("temperature_2m")
            val codes = hourlyObj.array("weather_code")
            val days = hourlyObj.array("is_day")
            times.indices.mapNotNull { i ->
                val time = times[i].text()?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() } ?: return@mapNotNull null
                val temp = temps.getOrNull(i)?.number() ?: return@mapNotNull null
                WeatherHour(time, temp, codes.getOrNull(i)?.number()?.toInt() ?: 3, days.getOrNull(i)?.flag() ?: true)
            }
        }
        val dailyObj = root["daily"] as JsonObject
        val dates = dailyObj.array("time")
        val dayCodes = dailyObj.array("weather_code")
        val highs = dailyObj.array("temperature_2m_max")
        val lows = dailyObj.array("temperature_2m_min")
        val daily = dates.indices.mapNotNull { i ->
            val date = dates[i].text()?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return@mapNotNull null
            WeatherDay(
                date,
                dayCodes.getOrNull(i)?.number()?.toInt() ?: 3,
                highs.getOrNull(i)?.number() ?: return@mapNotNull null,
                lows.getOrNull(i)?.number() ?: return@mapNotNull null
            )
        }
        val temperature = (current["temperature_2m"] as JsonPrimitive).doubleOrNull
        if (temperature == null || daily.isEmpty()) null else ForecastData(
            now = now,
            temperature = temperature,
            feelsLike = (current["apparent_temperature"] as? JsonPrimitive)?.doubleOrNull,
            code = (current["weather_code"] as? JsonPrimitive)?.number()?.toInt() ?: 3,
            isDay = (current["is_day"] as? JsonPrimitive)?.flag() ?: true,
            hourly = hourly,
            daily = daily
        )
    } catch (_: Exception) {
        null
    }

    /** The places the geocoder found, best match first; empty when it found none or answered with something else. */
    fun parsePlaces(body: String): List<GeoPlace>? = try {
        val root = json.parseToJsonElement(body) as JsonObject
        val results = root["results"] as? JsonArray
        results.orEmpty().mapNotNull { element ->
            val place = element as? JsonObject ?: return@mapNotNull null
            val name = place.string("name")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val latitude = (place["latitude"] as? JsonPrimitive)?.doubleOrNull ?: return@mapNotNull null
            val longitude = (place["longitude"] as? JsonPrimitive)?.doubleOrNull ?: return@mapNotNull null
            GeoPlace(
                name, place.string("admin1")?.takeIf { it.isNotBlank() }, place.string("country")?.takeIf { it.isNotBlank() },
                latitude, longitude, (place["population"] as? JsonPrimitive)?.longOrNull ?: 0L
            )
        }
    } catch (_: Exception) {
        null
    }

    fun forecastUrl(latitude: Double, longitude: Double, unit: TemperatureUnit): String = buildString {
        append("https://api.open-meteo.com/v1/forecast?latitude=").append(HomeLocationCodec.coordinate(latitude))
        append("&longitude=").append(HomeLocationCodec.coordinate(longitude))
        append("&current=temperature_2m,apparent_temperature,weather_code,is_day")
        append("&hourly=temperature_2m,weather_code,is_day")
        append("&daily=weather_code,temperature_2m_max,temperature_2m_min")
        unit.apiValue?.let { append("&temperature_unit=").append(it) }
        append("&timezone=auto&forecast_days=2")
    }

    fun geocodeUrl(name: String, language: String): String =
        "https://geocoding-api.open-meteo.com/v1/search?name=" + URLEncoder.encode(name.trim(), "UTF-8").replace("+", "%20") +
            "&count=5&language=" + URLEncoder.encode(language.ifBlank { "en" }, "UTF-8") + "&format=json"

    private fun JsonObject.string(name: String): String? = (this[name] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.array(name: String): List<JsonElement> = (this[name] as? JsonArray).orEmpty()
    private fun JsonElement.text(): String? = (this as? JsonPrimitive)?.contentOrNull
    private fun JsonElement.number(): Double? = (this as? JsonPrimitive)?.doubleOrNull
    private fun JsonElement.flag(): Boolean? = (this as? JsonPrimitive)?.let { it.intOrNull?.let { n -> n != 0 } ?: it.booleanOrNull }
}

/**
 * Forecasts (kept 15 minutes) and place lookups (kept a month) for the overlay's weather answers. The forecast
 * request carries only coordinates rounded to about a kilometre; a lookup carries the place name typed. Callers
 * check the privacy settings first; this class only fetches. The cache reads block: call off the main thread; a fetch
 * stops when its caller is cancelled.
 */
class WeatherRepository(
    private val http: HttpsGet,
    private val forecasts: AnswerCache<ForecastData>,
    private val places: AnswerCache<List<GeoPlace>>
) {
    fun cachedForecast(latitude: Double, longitude: Double, unit: TemperatureUnit): ForecastData? =
        forecasts.get(forecastKey(latitude, longitude, unit), OpenMeteoParser::parseForecast)

    fun peekForecast(latitude: Double, longitude: Double, unit: TemperatureUnit): ForecastData? =
        forecasts.peek(forecastKey(latitude, longitude, unit))

    /**
     * Fetches the forecast for the coordinates. [onDisk] false keeps it in memory only: a forecast for the phone's own
     * location would otherwise leave where the phone was in the app's files.
     */
    suspend fun fetchForecast(latitude: Double, longitude: Double, unit: TemperatureUnit, onDisk: Boolean = true): ForecastData? {
        val body = http.fetch(OpenMeteoParser.forecastUrl(latitude, longitude, unit), OpenMeteoParser.MAX_BYTES)
            ?.toString(Charsets.UTF_8) ?: return null
        val data = OpenMeteoParser.parseForecast(body) ?: return null
        forecasts.put(forecastKey(latitude, longitude, unit), data, body.takeIf { onDisk })
        return data
    }

    fun cachedPlaces(name: String, language: String): List<GeoPlace>? =
        places.get(placeKey(name, language)) { OpenMeteoParser.parsePlaces(it) }

    fun peekPlaces(name: String, language: String): List<GeoPlace>? = places.peek(placeKey(name, language))

    /** Places called [name]; empty when there are none, null when the lookup failed. */
    suspend fun fetchPlaces(name: String, language: String): List<GeoPlace>? {
        val body = http.fetch(OpenMeteoParser.geocodeUrl(name, language), OpenMeteoParser.MAX_BYTES)
            ?.toString(Charsets.UTF_8) ?: return null
        val found = OpenMeteoParser.parsePlaces(body) ?: return null
        places.put(placeKey(name, language), found, body)
        return found
    }

    /** Deletes the forecasts and places kept on disk past their lifetime. Touches the disk. */
    fun pruneExpired() {
        forecasts.pruneExpired()
        places.pruneExpired()
    }

    /** Forgets every forecast and place kept, for when the user turns Weather Answers off. */
    fun clear() {
        forecasts.clear()
        places.clear()
    }

    private fun forecastKey(latitude: Double, longitude: Double, unit: TemperatureUnit) =
        "${HomeLocationCodec.coordinate(latitude)},${HomeLocationCodec.coordinate(longitude)},${unit.name}"

    private fun placeKey(name: String, language: String) = "$language:" + name.trim().lowercase(Locale.ROOT)
}
