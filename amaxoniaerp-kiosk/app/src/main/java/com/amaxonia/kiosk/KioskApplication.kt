package com.amaxonia.kiosk

import android.app.Application
import com.amaxonia.kiosk.di.AppGraph

class KioskApplication : Application() {
    lateinit var appGraph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        appGraph = AppGraph(this)
    }

    companion object {
        lateinit var instance: KioskApplication
            private set
    }
}
