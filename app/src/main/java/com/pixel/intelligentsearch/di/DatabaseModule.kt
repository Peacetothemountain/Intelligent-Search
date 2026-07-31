package com.pixel.intelligentsearch.di
import android.content.Context
import androidx.room.Room
import com.pixel.intelligentsearch.core.data.HistoryDao
import com.pixel.intelligentsearch.core.data.IntelligentSearchDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): IntelligentSearchDatabase {
        return IntelligentSearchDatabase.getDatabase(context)
    }

    @Provides
    fun provideHistoryDao(database: IntelligentSearchDatabase): HistoryDao {
        return database.historyDao()
    }
}
