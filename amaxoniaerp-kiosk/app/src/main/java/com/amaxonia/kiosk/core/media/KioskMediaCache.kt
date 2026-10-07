package com.amaxonia.kiosk.core.media

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

private const val ONE_GIGABYTE_BYTES = 1024L * 1024L * 1024L

@OptIn(UnstableApi::class)
object KioskMediaCache {
    private var cacheInstance: SimpleCache? = null
    private var cacheDataSourceFactory: CacheDataSource.Factory? = null

    @Synchronized
    fun getCache(context: Context): SimpleCache {
        val existing = cacheInstance
        if (existing != null) return existing

        val cacheDir = File(context.cacheDir, "kiosk_media_cache")
        val evictor = LeastRecentlyUsedCacheEvictor(ONE_GIGABYTE_BYTES)
        val databaseProvider = StandaloneDatabaseProvider(context)
        val cache = SimpleCache(cacheDir, evictor, databaseProvider)
        cacheInstance = cache
        return cache
    }

    @Synchronized
    fun getCacheDataSourceFactory(context: Context): CacheDataSource.Factory {
        val existing = cacheDataSourceFactory
        if (existing != null) return existing

        val cache = getCache(context)
        val upstreamFactory = DefaultDataSource.Factory(context, DefaultHttpDataSource.Factory())
        val factory =
            CacheDataSource.Factory()
                .setCache(cache)
                .setUpstreamDataSourceFactory(upstreamFactory)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        cacheDataSourceFactory = factory
        return factory
    }
}
