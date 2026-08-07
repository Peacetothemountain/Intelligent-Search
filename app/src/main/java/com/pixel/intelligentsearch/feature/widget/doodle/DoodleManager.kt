package com.pixel.intelligentsearch.feature.widget.doodle

import com.pixel.intelligentsearch.R
import java.util.Calendar

object DoodleManager {

    data class HolidayDoodle(
        val name: String,
        val layoutResId: Int,
        val searchUrl: String = "https://www.google.com/search?q=${name.replace(" ", "+")}"
    )

    fun getCurrentHolidayDoodle(forceRandom: Boolean = false): HolidayDoodle? {
        if (forceRandom) {
            return allDoodles.random()
        }

        val cal = Calendar.getInstance()
        val month = cal.get(Calendar.MONTH) // 0-based
        val day = cal.get(Calendar.DAY_OF_MONTH)
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        val dayOfWeekInMonth = cal.get(Calendar.DAY_OF_WEEK_IN_MONTH)

        return allDoodles.find { holiday ->
            when (holiday.name) {
                "New Year's Day" -> month == Calendar.JANUARY && day == 1
                "Martin Luther King Jr. Day" -> month == Calendar.JANUARY && dayOfWeek == Calendar.MONDAY && dayOfWeekInMonth == 3
                "Valentine's Day" -> month == Calendar.FEBRUARY && day == 14
                "Presidents' Day" -> month == Calendar.FEBRUARY && dayOfWeek == Calendar.MONDAY && dayOfWeekInMonth == 3
                "St. Patrick's Day" -> month == Calendar.MARCH && day == 17
                "International Women's Day" -> month == Calendar.MARCH && day == 8
                "Earth Day" -> month == Calendar.APRIL && day == 22
                "Cinco de Mayo" -> month == Calendar.MAY && day == 5
                "Mother's Day" -> month == Calendar.MAY && dayOfWeek == Calendar.SUNDAY && dayOfWeekInMonth == 2
                "Memorial Day" -> month == Calendar.MAY && dayOfWeek == Calendar.MONDAY && day > 24
                "Pride Month" -> month == Calendar.JUNE
                "Juneteenth" -> month == Calendar.JUNE && day == 19
                "Father's Day" -> month == Calendar.JUNE && dayOfWeek == Calendar.SUNDAY && dayOfWeekInMonth == 3
                "Independence Day" -> month == Calendar.JULY && day == 4
                "Bastille Day" -> month == Calendar.JULY && day == 14
                "Labor Day" -> month == Calendar.SEPTEMBER && dayOfWeek == Calendar.MONDAY && dayOfWeekInMonth == 1
                "Patriot Day" -> month == Calendar.SEPTEMBER && day == 11
                "Oktoberfest" -> month == Calendar.SEPTEMBER && day > 15
                "Indigenous Peoples' Day" -> month == Calendar.OCTOBER && dayOfWeek == Calendar.MONDAY && dayOfWeekInMonth == 2
                "Halloween" -> month == Calendar.OCTOBER && day == 31
                "Day of the Dead" -> month == Calendar.NOVEMBER && (day == 1 || day == 2)
                "Veterans Day" -> month == Calendar.NOVEMBER && day == 11
                "Thanksgiving" -> month == Calendar.NOVEMBER && dayOfWeek == Calendar.THURSDAY && dayOfWeekInMonth == 4
                "Christmas" -> month == Calendar.DECEMBER && day == 25
                else -> false
            }
        }
    }

    private val allDoodles = listOf(
        HolidayDoodle("Bastille Day", R.layout.widget_doodle_flip_bastille_day),
        HolidayDoodle("Christmas", R.layout.widget_doodle_flip_christmas),
        HolidayDoodle("Cinco de Mayo", R.layout.widget_doodle_flip_cinco_de_mayo),
        HolidayDoodle("Day of the Dead", R.layout.widget_doodle_flip_day_of_the_dead),
        HolidayDoodle("Earth Day", R.layout.widget_doodle_flip_earth_day),
        HolidayDoodle("Father's Day", R.layout.widget_doodle_flip_fathers_day),
        HolidayDoodle("Halloween", R.layout.widget_doodle_flip_halloween),
        HolidayDoodle("Independence Day", R.layout.widget_doodle_flip_independence_day),
        HolidayDoodle("Indigenous Peoples' Day", R.layout.widget_doodle_flip_indigenous_peoples_day),
        HolidayDoodle("Juneteenth", R.layout.widget_doodle_flip_juneteenth),
        HolidayDoodle("Labor Day", R.layout.widget_doodle_flip_labor_day),
        HolidayDoodle("Martin Luther King Jr. Day", R.layout.widget_doodle_flip_martin_luther_king_jr_day),
        HolidayDoodle("Memorial Day", R.layout.widget_doodle_flip_memorial_day),
        HolidayDoodle("Mother's Day", R.layout.widget_doodle_flip_mothers_day),
        HolidayDoodle("New Year's Day", R.layout.widget_doodle_flip_new_years_day),
        HolidayDoodle("Oktoberfest", R.layout.widget_doodle_flip_oktoberfest),
        HolidayDoodle("Patriot Day", R.layout.widget_doodle_flip_patriot_day),
        HolidayDoodle("Presidents' Day", R.layout.widget_doodle_flip_presidents_day),
        HolidayDoodle("Pride Month", R.layout.widget_doodle_flip_pride_month),
        HolidayDoodle("St. Patrick's Day", R.layout.widget_doodle_flip_st_patricks_day),
        HolidayDoodle("Thanksgiving", R.layout.widget_doodle_flip_thanksgiving),
        HolidayDoodle("Valentine's Day", R.layout.widget_doodle_flip_valentines_day),
        HolidayDoodle("Veterans Day", R.layout.widget_doodle_flip_veterans_day),
        HolidayDoodle("International Women's Day", R.layout.widget_doodle_flip_international_womens_day)
    )
}
