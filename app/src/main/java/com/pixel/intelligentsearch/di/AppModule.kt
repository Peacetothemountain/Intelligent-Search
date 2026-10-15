package com.pixel.intelligentsearch.di

import com.pixel.intelligentsearch.core.system.WidgetUpdater
import com.pixel.intelligentsearch.feature.widget.SearchWidgetUpdater
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

/** The process-wide scope for work that must outlive any screen (indexing, startup, widget redraws). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    abstract fun bindWidgetUpdater(impl: SearchWidgetUpdater): WidgetUpdater

    companion object {
        /** One supervised scope instead of a separate unmanaged scope per class; a failing job can't cancel others. */
        @Provides
        @Singleton
        @ApplicationScope
        fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
