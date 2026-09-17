package com.pixel.intelligentsearch.feature.widget

import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SearchWidgetSystemProvider : SearchWidgetProvider() {
    override val forcedIsMaterial = false
}
