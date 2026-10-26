package com.pixel.intelligentsearch.feature.widget

import dagger.hilt.android.AndroidEntryPoint

/**
 * The home screen's own search bar, hosted by the launcher once this app is the search engine (set through the
 * search engine role or adb). It draws whichever design was last saved in Widget Customization. Pixel Launcher binds
 * it with no options, so this provider is the only way the widget can tell it is that bar.
 */
@AndroidEntryPoint
class SearchBoxWidgetProvider : SearchWidgetProvider()
