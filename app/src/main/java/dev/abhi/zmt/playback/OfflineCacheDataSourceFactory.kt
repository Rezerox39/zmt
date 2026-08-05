package dev.abhi.zmt.playback

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.CacheDataSink
import androidx.media3.datasource.cache.CacheDataSource
import dev.abhi.zmt.data.remote.telegram.CompositeDataSource
import javax.inject.Inject
import javax.inject.Singleton

private val CACHED_SCHEMES = setOf("http", "https", "tg", "youtube")

/**
 * Routes streamable sources through the [PlaybackCache] while leaving local
 * content:// reads untouched (no point copying device files into the cache).
 *
 * Falls back to the plain upstream chain when the cache cannot be initialised,
 * so playback behaviour never changes.
 */
@OptIn(UnstableApi::class)
@Singleton
class OfflineCacheDataSourceFactory @Inject constructor(
    private val upstream: CompositeDataSource.Factory,
    private val playbackCache: PlaybackCache,
) : DataSource.Factory {

    private val cacheFactory: DataSource.Factory? by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        playbackCache.cache()?.let { cache ->
            CacheDataSource.Factory()
                .setCache(cache)
                .setUpstreamDataSourceFactory(upstream)
                .setCacheWriteDataSinkFactory(CacheDataSink.Factory().setCache(cache))
                .setFlags(
                    CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR or
                        CacheDataSource.FLAG_IGNORE_CACHE_FOR_UNSET_LENGTH_REQUESTS,
                )
        }
    }

    override fun createDataSource(): DataSource {
        val upstreamDataSource = upstream.createDataSource()
        return SelectiveCacheDataSource(
            upstream = upstreamDataSource,
            cachedFactory = cacheFactory,
        )
    }
}

@OptIn(UnstableApi::class)
private class SelectiveCacheDataSource(
    private val upstream: DataSource,
    private val cachedFactory: DataSource.Factory?,
) : DataSource {

    private val listeners = mutableListOf<TransferListener>()
    private var active: DataSource = upstream

    override fun addTransferListener(transferListener: TransferListener) {
        upstream.addTransferListener(transferListener)
        listeners.add(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val factory = cachedFactory
        active = if (factory != null && dataSpec.uri.scheme in CACHED_SCHEMES) {
            factory.createDataSource().also { cached ->
                listeners.forEach { cached.addTransferListener(it) }
            }
        } else {
            upstream
        }
        return active.open(dataSpec)
    }

    override fun read(target: ByteArray, offset: Int, length: Int): Int =
        active.read(target, offset, length)

    override fun getResponseHeaders(): Map<String, List<String>> = active.responseHeaders

    override fun getUri(): Uri? = active.uri

    override fun close() {
        active.close()
    }
}
