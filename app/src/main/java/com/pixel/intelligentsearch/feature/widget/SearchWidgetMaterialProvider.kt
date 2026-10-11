package com.pixel.intelligentsearch.feature.widget

import dagger.hilt.android.AndroidEntryPoint

/**
 * Dedicated AppWidgetProvider for Material Design search bar widgets.
 * Forces Material You styling and dual-layer outer rim capsule aesthetics.
 */
@AndroidEntryPoint
class SearchWidgetMaterialProvider : SearchWidgetProvider() {
    override val forcedIsMaterial: Boolean = true
}
