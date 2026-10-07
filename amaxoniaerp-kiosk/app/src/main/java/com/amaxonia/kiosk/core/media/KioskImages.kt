package com.amaxonia.kiosk.core.media

import android.content.Context
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Size
import java.io.File

private const val MEMORY_CACHE_PERCENT = 0.25
private const val DISK_CACHE_BYTES = 256L * 1024L * 1024L
private const val PREFETCH_DECODE_PX = 64

/**
 * The single Coil [ImageLoader] of the app and the request shapes shared by the screens and the
 * preloader, so a preloaded image is a memory/disk cache hit when it is shown.
 */
object KioskImages {
    fun buildImageLoader(context: Context): ImageLoader =
        ImageLoader
            .Builder(context)
            .memoryCache { MemoryCache.Builder(context).maxSizePercent(MEMORY_CACHE_PERCENT).build() }
            .diskCache {
                DiskCache
                    .Builder()
                    .directory(File(context.cacheDir, "image_cache"))
                    .maxSizeBytes(DISK_CACHE_BYTES)
                    .build()
            }
            // The ERP serves images without cache headers: keep them anyway so the kiosk works offline
            // and never re-downloads a banner or product photo it already has.
            .respectCacheHeaders(false)
            .allowHardware(true)
            .crossfade(false)
            .build()

    /**
     * Attract banner: decoded at full size under a fixed key, so the request made by the preloader
     * and the one made on screen share the same memory cache entry (shown on the first frame).
     */
    fun bannerRequest(
        context: Context,
        url: String,
    ): ImageRequest =
        ImageRequest
            .Builder(context)
            .data(url)
            .memoryCacheKey(bannerMemoryKey(url))
            .size(Size.ORIGINAL)
            .build()

    /** Catalog photo prefetch: download to disk only (decoding every product up front would waste memory). */
    fun diskPrefetchRequest(
        context: Context,
        url: String,
    ): ImageRequest =
        ImageRequest
            .Builder(context)
            .data(url)
            .memoryCachePolicy(CachePolicy.DISABLED)
            // The bytes land in the disk cache untouched; decoding a tiny sample keeps the prefetch cheap.
            .size(PREFETCH_DECODE_PX)
            .build()

    private fun bannerMemoryKey(url: String) = MemoryCache.Key("banner:$url")
}
