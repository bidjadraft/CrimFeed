package com.example

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.example.data.remote.NetworkClient

class CrimsonFeedApplication : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        // Initialize HTTP conditional cache BEFORE rssHttpClient is first accessed
        NetworkClient.initHttpCache(cacheDir)
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .okHttpClient { NetworkClient.imageHttpClient }
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("crimson_image_cache"))
                    .maxSizeBytes(150L * 1024L * 1024L) // 150 MB persistent image cache
                    .build()
            }
            // Ignore strict CDN no-store/max-age=0 headers so viewed article images & feed icons
            // are downloaded ONCE when viewed and never re-fetched over the network.
            .respectCacheHeaders(false)
            .allowRgb565(true)
            .crossfade(false)
            .build()
    }
}
