package dev.jyotiraditya.dmt.di

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.SimpleCache
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TelegramModule {

    @Provides
    @Singleton
    fun defaultDataSourceFactory(
        @ApplicationContext context: Context,
    ): DefaultDataSource.Factory {
        return DefaultDataSource.Factory(context)
    }

    @OptIn(UnstableApi::class)
    @Provides
    @Singleton
    @Named("telegram_cache")
    fun telegramCache(
        @ApplicationContext context: Context,
    ): SimpleCache {
        val cacheDir = File(context.cacheDir, "telegram_audio_cache")
        cacheDir.mkdirs()
        return SimpleCache(
            cacheDir,
            androidx.media3.datasource.cache.NoOpCacheEvictor(),
        )
    }
}
