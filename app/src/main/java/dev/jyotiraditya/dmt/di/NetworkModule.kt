package dev.jyotiraditya.dmt.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import okhttp3.OkHttpClient
import okhttp3.OkHttpClient.Builder
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val DEFAULT_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

    @Provides
    @Singleton
    fun httpClient(): HttpClient =
        HttpClient(CIO) {
            install(HttpTimeout) {
                connectTimeoutMillis = 5_000
                socketTimeoutMillis = 15_000
            }
        }

    @Provides
    @Singleton
    fun okHttpClient(): OkHttpClient =
        Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", DEFAULT_USER_AGENT)
                    .header("Referer", "https://music.youtube.com/")
                    .header("Origin", "https://music.youtube.com")
                    .build()
                chain.proceed(request)
            }
            .build()
}
