package com.pixel.intelligentsearch.baselineprofile

import android.content.Intent
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until

const val TARGET_PACKAGE = "com.pixel.intelligentsearch"

/** Opens the search overlay the way the home-screen search bar does. */
fun MacrobenchmarkScope.openOverlay() {
    startActivityAndWait(
        Intent("com.android.launcher3.action.SEARCH")
            .setPackage(TARGET_PACKAGE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    )
}

/** The first thing people do in the overlay: type, see results appear, then clear the field. */
fun MacrobenchmarkScope.typeAQuery() {
    val field = device.wait(Until.findObject(By.clazz("android.widget.EditText")), 5_000) ?: return
    for (query in listOf("ca", "cal", "12*4")) {
        field.text = query
        device.waitForIdle()
    }
    field.text = ""
    device.waitForIdle()
}

/** The settings pages people open most, in the order the main page lists them. */
private val SETTINGS_PAGES = listOf("Appearance", "Search Shortcuts", "Search Behavior", "Widget Customization", "Launch Portal")

/**
 * Opens settings and visits each main page: open, fling down and up, go back. Settings ran the slowest first frames
 * (each page lays out for the first time as its transition starts), so this is what the profile should cover.
 */
fun MacrobenchmarkScope.browseSettings() {
    startActivityAndWait()
    for (title in SETTINGS_PAGES) {
        val row = device.wait(Until.findObject(By.text(title)), 5_000) ?: continue
        row.click()
        device.waitForIdle()
        device.findObject(By.scrollable(true))?.let { list ->
            list.setGestureMargin(device.displayWidth / 5)
            list.fling(Direction.DOWN)
            list.fling(Direction.UP)
        }
        device.waitForIdle()
        device.pressBack()
        device.wait(Until.hasObject(By.text(SETTINGS_PAGES.first())), 5_000)
    }
}
