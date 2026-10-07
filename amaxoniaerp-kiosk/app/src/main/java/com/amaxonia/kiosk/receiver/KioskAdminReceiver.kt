package com.amaxonia.kiosk.receiver

import android.app.admin.DeviceAdminReceiver
import android.content.ComponentName
import android.content.Context

class KioskAdminReceiver : DeviceAdminReceiver() {
    companion object {
        fun getComponentName(context: Context): ComponentName = ComponentName(context.applicationContext, KioskAdminReceiver::class.java)
    }
}
