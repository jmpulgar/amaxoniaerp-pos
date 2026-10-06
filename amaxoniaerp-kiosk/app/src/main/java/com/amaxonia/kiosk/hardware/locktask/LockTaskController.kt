package com.amaxonia.kiosk.hardware.locktask

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import com.amaxonia.kiosk.receiver.KioskAdminReceiver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class LockTaskController(
    private val context: Context,
    private val devicePolicyManager: DevicePolicyManager? =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager,
    private val adminComponent: ComponentName = KioskAdminReceiver.getComponentName(context),
) {
    private val _isLockTaskActive = MutableStateFlow(false)
    val isLockTaskActive: StateFlow<Boolean> = _isLockTaskActive.asStateFlow()

    fun isDeviceOwner(): Boolean = devicePolicyManager?.isDeviceOwnerApp(context.packageName) == true

    fun isLockTaskPermitted(): Boolean = devicePolicyManager?.isLockTaskPermitted(context.packageName) == true

    fun configureKioskPolicies() {
        if (!isDeviceOwner() || devicePolicyManager == null) {
            return
        }

        try {
            devicePolicyManager.setLockTaskPackages(
                adminComponent,
                arrayOf(context.packageName),
            )
            devicePolicyManager.setLockTaskFeatures(
                adminComponent,
                DevicePolicyManager.LOCK_TASK_FEATURE_NONE,
            )
            devicePolicyManager.setKeyguardDisabled(adminComponent, true)
            devicePolicyManager.setStatusBarDisabled(adminComponent, true)
        } catch (_: SecurityException) {
            // Ignored if permissions are not granted yet
        }
    }

    @Suppress("TooGenericExceptionCaught")
    fun startLockTask(activity: Activity) {
        try {
            if (isDeviceOwner()) {
                configureKioskPolicies()
            }
            activity.startLockTask()
            _isLockTaskActive.value = true
        } catch (_: Exception) {
            // Fallback for non-rooted / non-device-owner dev environments
        }
    }

    @Suppress("TooGenericExceptionCaught")
    fun stopLockTask(activity: Activity) {
        try {
            activity.stopLockTask()
            _isLockTaskActive.value = false
        } catch (_: Exception) {
            // Ignored if not in lock task
        }
    }
}
