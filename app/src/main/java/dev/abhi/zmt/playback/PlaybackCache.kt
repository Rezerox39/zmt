package dev.abhi.zmt.playback

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheKeyFactory
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.Volatile

private const val CACHE_DIR_NAME = "playback_cache"
private const val MAX_CACHE_BYTES = 2L * 1024 * 1024 * 1024

/**
 * On-disk smart cache for every streamed source (YouTube, Telegram, Jellyfin).
 *
 * The cache is keyed by the DataSource URI, so YouTube tracks keep the stable
 * `youtube://video/<id>` key even when the resolved CDN URL changes between
 * plays — repeat listens are served from disk without a network request.
 *
 * Initialisation is lazy and happens on the first playback-thread request so
 * app startup and UI stay on the main thread untouched.
 */
@OptIn(UnstableApi::class)
@Singleton
class PlaybackCache @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val lock = Any()

    @Volatile
    private var cache: SimpleCache? = null

    fun cache(): SimpleCache? = cache ?: synchronized(lock) {
        cache ?: runCatching {
            val dir = File(context.filesDir, CACHE_DIR_NAME).apply { mkdirs() }
            SimpleCache(
                dir,
                LeastRecentlyUsedCacheEvictor(MAX_CACHE_BYTES),
                StandaloneDatabaseProvider(context),
            ).also { cache = it }
        }.getOrNull()
    }

    /** Number of bytes currently cached for [uri], or 0 when nothing is stored. */
    fun cachedBytes(uri: Uri?): Long {
        val simple = cache ?: return 0L
        if (uri == null) return 0L
        return runCatching {
            val key = CacheKeyFactory.DEFAULT.buildCacheKey(DataSpec(uri))
            simple.getCachedBytes(key, 0L, Long.MAX_VALUE)
        }.getOrDefault(0L)
    }

    /** Total content length recorded in the cache for [uri], or [C.LENGTH_UNSET]. */
    fun contentLength(uri: Uri?): Long {
        val simple = cache ?: return C.LENGTH_UNSET.toLong()
        if (uri == null) return C.LENGTH_UNSET.toLong()
        return runCatching {
            val key = CacheKeyFactory.DEFAULT.buildCacheKey(DataSpec(uri))
            simple.getContentMetadata(key)
                .get(ContentMetadata.KEY_CONTENT_LENGTH, C.LENGTH_UNSET.toLong())
        }.getOrDefault(C.LENGTH_UNSET.toLong())
    }
}
