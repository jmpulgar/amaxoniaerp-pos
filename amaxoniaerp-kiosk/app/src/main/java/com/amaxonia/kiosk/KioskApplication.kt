package com.amaxonia.kiosk

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.amaxonia.kiosk.core.media.KioskImages
import com.amaxonia.kiosk.di.AppGraph

class KioskApplication :
    Application(),
    ImageLoaderFactory {
    lateinit var appGraph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        appGraph = AppGraph(this)
        appGraph.start()
    }

    /** App-wide Coil loader: shared memory + disk cache used by every screen and the media preloader. */
    override fun newImageLoader(): ImageLoader = KioskImages.buildImageLoader(this)

    companion object {
        lateinit var instance: KioskApplication
            private set
    }
}
