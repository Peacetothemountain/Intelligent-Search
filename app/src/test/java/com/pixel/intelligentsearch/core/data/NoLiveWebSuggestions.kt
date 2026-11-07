package com.pixel.intelligentsearch.core.data

import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn

/**
 * Hilt tests ask no engine for live suggestions. Web Suggestions is on by default, so every test that typed a query
 * with the real [DeviceSearchSourcesImpl] sent it to Google, and a late request could reach the local server of a
 * test that had pointed [WebSearchProvider.openForTests] at itself. Tests of suggestions use their own sources or
 * call [WebSearchProvider] directly.
 */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [WebSuggestionServiceModule::class])
object NoLiveWebSuggestionsModule {
    @Provides
    fun provideWebSuggestionService(): WebSuggestionService = WebSuggestionService { _, _ -> emptyList() }
}
