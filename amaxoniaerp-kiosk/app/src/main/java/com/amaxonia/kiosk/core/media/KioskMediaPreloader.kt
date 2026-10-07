package com.amaxonia.kiosk.core.media

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheWriter
import coil.imageLoader
import com.amaxonia.kiosk.core.network.KioskCatalogResponse
import com.amaxonia.kiosk.core.network.KioskMediaItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Collections

private const val TAG = "KioskMediaPreloader"

/**
 * Downloads the attract banners (images and videos) and the catalog photos ahead of time, so the
 * banner loop never waits on the network (no black frame between banners) and the menu opens with
 * its photos already on disk. Each URL is fetched once per process; Coil / ExoPlayer caches keep it.
 */
class KioskMediaPreloader(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private val requested: MutableSet<String> = Collections.synchronizedSet(HashSet())

    fun preloadBanners(media: List<KioskMediaItem>) {
        media.forEach { item ->
            val url = item.url.takeIf(String::isNotBlank) ?: return@forEach
            when {
                item.type.equals("IMAGE", ignoreCase = true) ->
                    // Memory + disk: the next banner must be ready on the very first frame.
                    context.imageLoader.enqueue(KioskImages.bannerRequest(context, url))
                item.type.equals("VIDEO", ignoreCase = true) ->
                    if (requested.add("video:$url")) scope.launch(Dispatchers.IO) { cacheVideo(url) }
            }
        }
    }

    fun preloadCatalog(catalog: KioskCatalogResponse) {
        val urls = catalog.categories.mapNotNull { it.iconUrl } + catalog.items.mapNotNull { it.imageUrl }
        urls
            .filter { it.isNotBlank() && requested.add("img:$it") }
            .forEach { context.imageLoader.enqueue(KioskImages.diskPrefetchRequest(context, it)) }
    }

    @OptIn(UnstableApi::class)
    private fun cacheVideo(url: String) {
        runCatching {
            val dataSource = KioskMediaCache.getCacheDataSourceFactory(context).createDataSource()
            CacheWriter(dataSource, DataSpec(Uri.parse(url)), null, null).cache()
        }.onFailure {
            // Retried on the next config load; playback still streams through the cache meanwhile.
            requested.remove("video:$url")
            Log.w(TAG, "No se pudo precargar el video $url: ${it.message}")
        }
    }
}
