package com.pixel.intelligentsearch.core.occasion

import androidx.annotation.LayoutRes
import androidx.annotation.StringRes
import com.pixel.intelligentsearch.R

/**
 * The days the widget's G gets a doodle: a small animated scene drawn around it, made for this app and drawn on the
 * phone (`res/drawable/doodle_*`, an animated vector each, shown through `res/layout/doodle_*`). Nothing is fetched.
 * [title] is what TalkBack reads and what tapping the doodle searches for.
 */
enum class Occasion(@param:StringRes val title: Int, @param:LayoutRes val layout: Int) {
    NEW_YEARS_DAY(R.string.occasion_new_years_day, R.layout.doodle_new_years_day),
    VALENTINES_DAY(R.string.occasion_valentines_day, R.layout.doodle_valentines_day),
    LEAP_DAY(R.string.occasion_leap_day, R.layout.doodle_leap_day),
    EARTH_DAY(R.string.occasion_earth_day, R.layout.doodle_earth_day),
    FIRST_DAY_OF_SPRING(R.string.occasion_first_day_of_spring, R.layout.doodle_spring),
    FIRST_DAY_OF_SUMMER(R.string.occasion_first_day_of_summer, R.layout.doodle_summer),
    FIRST_DAY_OF_AUTUMN(R.string.occasion_first_day_of_autumn, R.layout.doodle_autumn),
    FIRST_DAY_OF_WINTER(R.string.occasion_first_day_of_winter, R.layout.doodle_winter),
    HALLOWEEN(R.string.occasion_halloween, R.layout.doodle_halloween),
    HOLIDAYS(R.string.occasion_holidays, R.layout.doodle_holidays),
    NEW_YEARS_EVE(R.string.occasion_new_years_eve, R.layout.doodle_new_years_eve)
}
