package dev.abhi.zmt.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.abhi.zmt.data.repository.JellyfinMediaRepositoryImpl
import dev.abhi.zmt.data.repository.MediaRepositoryImpl
import dev.abhi.zmt.data.repository.TelegramMediaRepositoryImpl
import dev.abhi.zmt.data.repository.YoutubeMediaRepositoryImpl
import dev.abhi.zmt.domain.repository.MediaRepository

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Local
    @Binds
    abstract fun mediaRepository(impl: MediaRepositoryImpl): MediaRepository

    @JellyfinSource
    @Binds
    abstract fun jellyfinMediaRepository(impl: JellyfinMediaRepositoryImpl): MediaRepository

    @TelegramSource
    @Binds
    abstract fun telegramMediaRepository(impl: TelegramMediaRepositoryImpl): MediaRepository

    @YouTubeSource
    @Binds
    abstract fun youtubeMediaRepository(impl: YoutubeMediaRepositoryImpl): MediaRepository
}
