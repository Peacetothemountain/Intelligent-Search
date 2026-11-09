package com.pixel.intelligentsearch.feature.search.today.cards.widget

/*
 * What the app and the widget reader process say to each other. Both sides bind to the same widget host, and every
 * message carries only plain values (ids, short strings, a tiny JSON model), so nothing a widget made ever crosses into
 * the app's process.
 *
 * NG Designs
 */

/** The one widget host Discover's app cards are bound to, shared by the app (adding, removing) and the reader. */
internal const val WIDGET_HOST_ID = 0x15D1

/** The size widgets are laid out at for reading, in dp: the size the reader was proven at. */
internal const val READ_SIZE_DP = 180

/** App to reader: read the widgets in [KEY_IDS]. */
internal const val MSG_READ = 1

/** App to reader: which of the providers in [KEY_PROVIDERS] are only a picture; answered with [MSG_CHOICES_DONE]. */
internal const val MSG_CHOICES = 2

/** Reader to app: one read of widget [KEY_ID]. */
internal const val MSG_CARD = 3

/** Reader to app: the picture-only providers ([KEY_PROVIDERS]) for request [KEY_REQUEST]. */
internal const val MSG_CHOICES_DONE = 4

internal const val KEY_IDS = "ids"
internal const val KEY_ID = "id"
internal const val KEY_VERDICT = "verdict"
internal const val KEY_MODEL = "model"
internal const val KEY_FP = "fp"
internal const val KEY_PROVIDERS = "providers"
internal const val KEY_REQUEST = "request"
