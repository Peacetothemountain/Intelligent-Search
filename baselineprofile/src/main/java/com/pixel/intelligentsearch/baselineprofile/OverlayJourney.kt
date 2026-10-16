package com.pixel.intelligentsearch.baselineprofile

import android.content.Intent
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
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
