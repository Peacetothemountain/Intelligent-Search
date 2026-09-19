package com.pixel.intelligentsearch.feature.widget

import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SearchWidgetMaterialProvider : SearchWidgetProvider() {
    override val forcedIsMaterial = true
}
