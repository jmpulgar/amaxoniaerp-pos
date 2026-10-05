package com.amaxonia.kiosk

import android.app.Application

class KioskApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: KioskApplication
            private set
    }
}
