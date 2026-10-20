package com.pixel.intelligentsearch.core.occasion

import androidx.annotation.LayoutRes
import androidx.annotation.StringRes
import com.pixel.intelligentsearch.R

/**
 * The days the widget's G gets a doodle: a small animated scene drawn around it, made for this app and drawn on the
 * phone (`res/drawable/doodle_*`, an animated vector each, shown through `res/layout/doodle_*`). Nothing is fetched.
 * [title] is what TalkBack reads and what tapping the doodle searches for. Listed in calendar order.
 */
enum class Occasion(@param:StringRes val title: Int, @param:LayoutRes val layout: Int) {
    NEW_YEARS_DAY(R.string.occasion_new_years_day, R.layout.doodle_new_years_day),
    LUNAR_NEW_YEAR(R.string.occasion_lunar_new_year, R.layout.doodle_lunar_new_year),
    GROUNDHOG_DAY(R.string.occasion_groundhog_day, R.layout.doodle_groundhog_day),
    VALENTINES_DAY(R.string.occasion_valentines_day, R.layout.doodle_valentines_day),
    LEAP_DAY(R.string.occasion_leap_day, R.layout.doodle_leap_day),
    WOMENS_DAY(R.string.occasion_womens_day, R.layout.doodle_womens_day),
    PI_DAY(R.string.occasion_pi_day, R.layout.doodle_pi_day),
    ST_PATRICKS_DAY(R.string.occasion_st_patricks_day, R.layout.doodle_st_patricks_day),
    FIRST_DAY_OF_SPRING(R.string.occasion_first_day_of_spring, R.layout.doodle_spring),
    APRIL_FOOLS_DAY(R.string.occasion_april_fools_day, R.layout.doodle_april_fools_day),
    EASTER(R.string.occasion_easter, R.layout.doodle_easter),
    EARTH_DAY(R.string.occasion_earth_day, R.layout.doodle_earth_day),
    MOTHERS_DAY(R.string.occasion_mothers_day, R.layout.doodle_mothers_day),
    BEE_DAY(R.string.occasion_bee_day, R.layout.doodle_bee_day),
    OCEANS_DAY(R.string.occasion_oceans_day, R.layout.doodle_oceans_day),
    FATHERS_DAY(R.string.occasion_fathers_day, R.layout.doodle_fathers_day),
    FIRST_DAY_OF_SUMMER(R.string.occasion_first_day_of_summer, R.layout.doodle_summer),
    CANADA_DAY(R.string.occasion_canada_day, R.layout.doodle_canada_day),
    INDEPENDENCE_DAY(R.string.occasion_independence_day, R.layout.doodle_independence_day),
    CHOCOLATE_DAY(R.string.occasion_chocolate_day, R.layout.doodle_chocolate_day),
    EMOJI_DAY(R.string.occasion_emoji_day, R.layout.doodle_emoji_day),
    MOON_DAY(R.string.occasion_moon_day, R.layout.doodle_moon_day),
    CAT_DAY(R.string.occasion_cat_day, R.layout.doodle_cat_day),
    DOG_DAY(R.string.occasion_dog_day, R.layout.doodle_dog_day),
    PIRATE_DAY(R.string.occasion_pirate_day, R.layout.doodle_pirate_day),
    FIRST_DAY_OF_AUTUMN(R.string.occasion_first_day_of_autumn, R.layout.doodle_autumn),
    COFFEE_DAY(R.string.occasion_coffee_day, R.layout.doodle_coffee_day),
    TEACHERS_DAY(R.string.occasion_teachers_day, R.layout.doodle_teachers_day),
    HALLOWEEN(R.string.occasion_halloween, R.layout.doodle_halloween),
    KINDNESS_DAY(R.string.occasion_kindness_day, R.layout.doodle_kindness_day),
    THANKSGIVING(R.string.occasion_thanksgiving, R.layout.doodle_thanksgiving),
    FIRST_DAY_OF_WINTER(R.string.occasion_first_day_of_winter, R.layout.doodle_winter),
    HOLIDAYS(R.string.occasion_holidays, R.layout.doodle_holidays),
    NEW_YEARS_EVE(R.string.occasion_new_years_eve, R.layout.doodle_new_years_eve)
}
